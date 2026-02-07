use anyhow::Result;
use hefesto_domain::portinfo::port_binding::{ConnectionState, PortBinding, Protocol};
use regex::Regex;
use std::process::Command;
use std::sync::LazyLock;

use super::PortParser;

// ss output pattern: LISTEN  0  128  0.0.0.0:8080  0.0.0.0:*  users:(("java",pid=1234,fd=5))
static SS_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r#"(LISTEN|ESTAB|TIME-WAIT|CLOSE-WAIT)\s+\d+\s+\d+\s+([\d.:*]+):(\d+)\s+([\d.:*]+):(\d+|\*)\s*(?:users:\(\("([^"]+)",pid=(\d+).*\)\))?"#
    ).unwrap()
});

// lsof output pattern
static LSOF_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"(\S+)\s+(\d+)\s+(\S+)\s+\S+\s+\S+\s+\S+\s+\S+\s+(TCP|UDP)\s+([^:]+):(\d+|\*)(?:->([^:]+):(\d+))?\s*\((\w+)\)?"
    ).unwrap()
});

pub struct LinuxPortParser;

impl LinuxPortParser {
    pub fn new() -> Self {
        Self
    }

    fn run_ss(&self, args: &[&str]) -> Vec<PortBinding> {
        let mut bindings = Vec::new();

        let output = Command::new("ss").args(args).output();

        if let Ok(output) = output {
            let stdout = String::from_utf8_lossy(&output.stdout);
            for line in stdout.lines() {
                if let Some(binding) = self.parse_ss_line(line) {
                    bindings.push(binding);
                }
            }
        }

        bindings
    }

    fn run_lsof(&self, args: &[&str]) -> Vec<PortBinding> {
        let mut bindings = Vec::new();

        let output = Command::new("lsof").args(args).output();

        if let Ok(output) = output {
            let stdout = String::from_utf8_lossy(&output.stdout);
            for line in stdout.lines() {
                if let Some(binding) = self.parse_lsof_line(line) {
                    bindings.push(binding);
                }
            }
        }

        bindings
    }

    fn parse_ss_line(&self, line: &str) -> Option<PortBinding> {
        let caps = SS_PATTERN.captures(line)?;

        let state_str = caps.get(1)?.as_str();
        let local_addr = caps.get(2)?.as_str().to_string();
        let port: u16 = caps.get(3)?.as_str().parse().ok()?;
        let remote_addr = caps.get(4)?.as_str().to_string();
        let remote_port_str = caps.get(5)?.as_str();
        let process_name = caps.get(6).map(|m| m.as_str().to_string()).unwrap_or_default();
        let pid: u32 = caps.get(7).and_then(|m| m.as_str().parse().ok()).unwrap_or(0);

        let remote_port: u16 = if remote_port_str == "*" {
            0
        } else {
            remote_port_str.parse().unwrap_or(0)
        };

        let state = ConnectionState::from_str_loose(state_str);

        Some(PortBinding {
            port,
            protocol: Protocol::Tcp,
            state,
            pid,
            process_name,
            command_line: String::new(),
            user: String::new(),
            local_address: local_addr,
            remote_address: remote_addr,
            remote_port,
        })
    }

    fn parse_lsof_line(&self, line: &str) -> Option<PortBinding> {
        let caps = LSOF_PATTERN.captures(line)?;

        let process_name = caps.get(1)?.as_str().to_string();
        let pid: u32 = caps.get(2)?.as_str().parse().ok()?;
        let user = caps.get(3)?.as_str().to_string();
        let protocol_str = caps.get(4)?.as_str();
        let local_addr = caps.get(5)?.as_str().to_string();
        let port_str = caps.get(6)?.as_str();
        let remote_addr = caps.get(7).map(|m| m.as_str().to_string()).unwrap_or_default();
        let remote_port_str = caps.get(8).map(|m| m.as_str());
        let state_str = caps.get(9).map(|m| m.as_str()).unwrap_or("");

        if port_str == "*" {
            return None;
        }
        let port: u16 = port_str.parse().ok()?;
        let remote_port: u16 = remote_port_str.and_then(|s| s.parse().ok()).unwrap_or(0);

        let protocol = Protocol::from_str_loose(protocol_str);
        let state = ConnectionState::from_str_loose(state_str);

        Some(PortBinding {
            port,
            protocol,
            state,
            pid,
            process_name,
            command_line: String::new(),
            user,
            local_address: local_addr,
            remote_address: remote_addr,
            remote_port,
        })
    }
}

impl PortParser for LinuxPortParser {
    fn find_by_port(&self, port: u16, tcp: bool, udp: bool) -> Result<Vec<PortBinding>> {
        let mut bindings = Vec::new();
        let port_str = port.to_string();

        if tcp {
            let mut result = self.run_ss(&["-tlnp", "sport", "=", &format!(":{}", port_str)]);
            if result.is_empty() {
                result = self.run_lsof(&["-nP", &format!("-iTCP:{}", port_str)]);
            }
            bindings.extend(result);
        }

        if udp {
            let mut result = self.run_ss(&["-ulnp", "sport", "=", &format!(":{}", port_str)]);
            if result.is_empty() {
                result = self.run_lsof(&["-nP", &format!("-iUDP:{}", port_str)]);
            }
            bindings.extend(result);
        }

        Ok(bindings)
    }

    fn find_by_pid(&self, pid: u32) -> Result<Vec<PortBinding>> {
        Ok(self.run_lsof(&["-nP", "-p", &pid.to_string(), "-iTCP", "-iUDP"]))
    }

    fn find_in_range(&self, from: u16, to: u16, listen_only: bool) -> Result<Vec<PortBinding>> {
        let flag = if listen_only { "-tlnp" } else { "-tanp" };
        let bindings: Vec<PortBinding> = self
            .run_ss(&[flag])
            .into_iter()
            .filter(|b| b.port >= from && b.port <= to)
            .collect();

        Ok(bindings)
    }

    fn find_all_listening(&self) -> Result<Vec<PortBinding>> {
        let mut bindings = self.run_ss(&["-tlnp"]);
        if bindings.is_empty() {
            bindings = self.run_lsof(&["-nP", "-iTCP", "-sTCP:LISTEN"]);
        }
        Ok(bindings)
    }

    fn find_all(&self, tcp: bool, udp: bool) -> Result<Vec<PortBinding>> {
        let mut flags = String::from("-");
        if tcp {
            flags.push('t');
        }
        if udp {
            flags.push('u');
        }
        flags.push_str("anp");

        Ok(self.run_ss(&[&flags]))
    }

    fn find_by_process_name(&self, process_name: &str) -> Result<Vec<PortBinding>> {
        let lower_name = process_name.to_lowercase();
        let all = self.find_all(true, true)?;

        Ok(all
            .into_iter()
            .filter(|b| b.process_name.to_lowercase().contains(&lower_name))
            .collect())
    }

    fn kill_process(&self, pid: u32, force: bool) -> Result<bool> {
        let signal = if force { "-9" } else { "-15" };
        let status = Command::new("kill")
            .args([signal, &pid.to_string()])
            .status()?;

        Ok(status.success())
    }
}
