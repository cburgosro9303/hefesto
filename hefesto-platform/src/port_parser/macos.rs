use anyhow::Result;
use hefesto_domain::portinfo::port_binding::{ConnectionState, PortBinding, Protocol};
use regex::Regex;
use std::process::Command;
use std::sync::LazyLock;

use super::PortParser;

// lsof -nP output: java      1234  user   5u  IPv4 0x...  0t0  TCP *:8080 (LISTEN)
// Or with connection: java  1234  user   5u  IPv4 0x...  0t0  TCP 127.0.0.1:8080->127.0.0.1:54321 (ESTABLISHED)
static LSOF_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"^(\S+)\s+(\d+)\s+(\S+)\s+\S+\s+(IPv[46])\s+\S+\s+\S+\s+(TCP|UDP)\s+([^:]+|\*):([\d*]+)(?:->([^:]+):(\d+))?\s*(?:\(([^)]+)\))?"
    ).unwrap()
});

pub struct MacOsPortParser;

impl MacOsPortParser {
    pub fn new() -> Self {
        Self
    }

    fn run_lsof(&self, args: &[&str]) -> Vec<PortBinding> {
        let mut bindings = Vec::new();

        let output = Command::new("lsof")
            .args(args)
            .output();

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

    fn parse_lsof_line(&self, line: &str) -> Option<PortBinding> {
        let caps = LSOF_PATTERN.captures(line)?;

        let process_name = caps.get(1)?.as_str().to_string();
        let pid: u32 = caps.get(2)?.as_str().parse().ok()?;
        let user = caps.get(3)?.as_str().to_string();
        let protocol_str = caps.get(5)?.as_str();
        let mut local_addr = caps.get(6)?.as_str().to_string();
        let port_str = caps.get(7)?.as_str();
        let remote_addr = caps.get(8).map(|m| m.as_str().to_string()).unwrap_or_default();
        let remote_port_str = caps.get(9).map(|m| m.as_str());
        let state_str = caps.get(10).map(|m| m.as_str()).unwrap_or("");

        // Skip header lines or invalid entries
        if port_str == "*" || port_str.is_empty() {
            return None;
        }

        let port: u16 = port_str.parse().ok()?;
        let remote_port: u16 = remote_port_str
            .and_then(|s| s.parse().ok())
            .unwrap_or(0);

        // Normalize local address
        if local_addr == "*" {
            local_addr = "0.0.0.0".to_string();
        }

        let protocol = Protocol::from_str_loose(protocol_str);
        let state = ConnectionState::from_str_loose(state_str);

        // Get command line
        let command_line = get_command_line(pid);

        Some(PortBinding {
            port,
            protocol,
            state,
            pid,
            process_name,
            command_line,
            user,
            local_address: local_addr,
            remote_address: remote_addr,
            remote_port,
        })
    }
}

impl PortParser for MacOsPortParser {
    fn find_by_port(&self, port: u16, tcp: bool, udp: bool) -> Result<Vec<PortBinding>> {
        let mut bindings = Vec::new();

        if tcp {
            bindings.extend(self.run_lsof(&["-nP", &format!("-iTCP:{}", port)]));
        }
        if udp {
            bindings.extend(self.run_lsof(&["-nP", &format!("-iUDP:{}", port)]));
        }

        Ok(bindings)
    }

    fn find_by_pid(&self, pid: u32) -> Result<Vec<PortBinding>> {
        Ok(self.run_lsof(&["-nP", "-p", &pid.to_string(), "-iTCP", "-iUDP"]))
    }

    fn find_in_range(&self, from: u16, to: u16, listen_only: bool) -> Result<Vec<PortBinding>> {
        let mut args = vec!["-nP", "-iTCP"];
        if listen_only {
            args.push("-sTCP:LISTEN");
        }

        let bindings: Vec<PortBinding> = self
            .run_lsof(&args)
            .into_iter()
            .filter(|b| b.port >= from && b.port <= to)
            .collect();

        Ok(bindings)
    }

    fn find_all_listening(&self) -> Result<Vec<PortBinding>> {
        Ok(self.run_lsof(&["-nP", "-iTCP", "-sTCP:LISTEN"]))
    }

    fn find_all(&self, tcp: bool, udp: bool) -> Result<Vec<PortBinding>> {
        let mut args = vec!["-nP"];

        if tcp && udp {
            args.push("-iTCP");
            args.push("-iUDP");
        } else if tcp {
            args.push("-iTCP");
        } else if udp {
            args.push("-iUDP");
        } else {
            return Ok(Vec::new());
        }

        Ok(self.run_lsof(&args))
    }

    fn find_by_process_name(&self, process_name: &str) -> Result<Vec<PortBinding>> {
        let lower_name = process_name.to_lowercase();
        let all = self.run_lsof(&["-nP", "-iTCP", "-iUDP"]);

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

fn get_command_line(pid: u32) -> String {
    Command::new("ps")
        .args(["-p", &pid.to_string(), "-o", "command="])
        .output()
        .ok()
        .and_then(|output| {
            let stdout = String::from_utf8_lossy(&output.stdout);
            let line = stdout.lines().next()?;
            Some(line.trim().to_string())
        })
        .unwrap_or_default()
}
