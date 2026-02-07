use anyhow::Result;
use hefesto_domain::portinfo::port_binding::{ConnectionState, PortBinding, Protocol};
use regex::Regex;
use std::collections::HashMap;
use std::process::Command;
use std::sync::{LazyLock, Mutex};

use super::PortParser;

// netstat -ano output: TCP    0.0.0.0:8080    0.0.0.0:0    LISTENING    1234
static NETSTAT_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"^\s*(TCP|UDP)\s+([\d.:]+|\[::\]):([\d]+)\s+([\d.:]+|\[::\]|\*:\*):([\d]+|\*)\s*(LISTENING|ESTABLISHED|TIME_WAIT|CLOSE_WAIT|FIN_WAIT_\d)?\s*(\d+)?\s*$"
    ).unwrap()
});

pub struct WindowsPortParser {
    process_name_cache: Mutex<HashMap<u32, String>>,
}

impl WindowsPortParser {
    pub fn new() -> Self {
        Self {
            process_name_cache: Mutex::new(HashMap::new()),
        }
    }

    fn get_all_bindings(&self, tcp: bool, udp: bool) -> Vec<PortBinding> {
        let mut bindings = Vec::new();
        {
            let mut cache = self.process_name_cache.lock().unwrap();
            cache.clear();
        }

        let mut args = vec!["netstat", "-ano"];
        if tcp && !udp {
            args.extend(["-p", "tcp"]);
        } else if udp && !tcp {
            args.extend(["-p", "udp"]);
        }

        let output = Command::new(args[0])
            .args(&args[1..])
            .output();

        if let Ok(output) = output {
            let stdout = String::from_utf8_lossy(&output.stdout);
            for line in stdout.lines() {
                if let Some(binding) = self.parse_netstat_line(line) {
                    bindings.push(binding);
                }
            }
        }

        bindings
    }

    fn parse_netstat_line(&self, line: &str) -> Option<PortBinding> {
        let caps = NETSTAT_PATTERN.captures(line)?;

        let protocol_str = caps.get(1)?.as_str();
        let local_addr = normalize_address(caps.get(2)?.as_str());
        let port: u16 = caps.get(3)?.as_str().parse().ok()?;

        let remote_addr = normalize_address(caps.get(4)?.as_str());
        let remote_port_str = caps.get(5)?.as_str();
        let remote_port: u16 = if remote_port_str == "*" {
            0
        } else {
            remote_port_str.parse().unwrap_or(0)
        };

        let mut state_str = caps.get(6).map(|m| m.as_str()).unwrap_or("");
        // Normalize state name
        if state_str == "LISTENING" {
            state_str = "LISTEN";
        }

        let pid_str = caps.get(7)?.as_str();
        if pid_str.is_empty() {
            return None;
        }
        let pid: u32 = pid_str.parse().ok()?;

        let protocol = Protocol::from_str_loose(protocol_str);
        let state = ConnectionState::from_str_loose(state_str);
        let process_name = self.get_process_name(pid);
        let command_line = get_command_line(pid);

        Some(PortBinding {
            port,
            protocol,
            state,
            pid,
            process_name,
            command_line,
            user: String::new(),
            local_address: local_addr,
            remote_address: remote_addr,
            remote_port,
        })
    }

    fn get_process_name(&self, pid: u32) -> String {
        {
            let cache = self.process_name_cache.lock().unwrap();
            if let Some(name) = cache.get(&pid) {
                return name.clone();
            }
        }

        let name = Command::new("tasklist")
            .args(["/fo", "csv", "/fi", &format!("PID eq {}", pid)])
            .output()
            .ok()
            .and_then(|output| {
                let stdout = String::from_utf8_lossy(&output.stdout);
                let mut lines = stdout.lines();
                lines.next(); // skip header
                let line = lines.next()?;
                let parts: Vec<&str> = line.split(',').collect();
                if !parts.is_empty() {
                    Some(parts[0].replace('"', "").trim().to_string())
                } else {
                    None
                }
            })
            .unwrap_or_default();

        {
            let mut cache = self.process_name_cache.lock().unwrap();
            cache.insert(pid, name.clone());
        }

        name
    }
}

impl PortParser for WindowsPortParser {
    fn find_by_port(&self, port: u16, tcp: bool, udp: bool) -> Result<Vec<PortBinding>> {
        Ok(self
            .get_all_bindings(tcp, udp)
            .into_iter()
            .filter(|b| b.port == port)
            .collect())
    }

    fn find_by_pid(&self, pid: u32) -> Result<Vec<PortBinding>> {
        Ok(self
            .get_all_bindings(true, true)
            .into_iter()
            .filter(|b| b.pid == pid)
            .collect())
    }

    fn find_in_range(&self, from: u16, to: u16, listen_only: bool) -> Result<Vec<PortBinding>> {
        Ok(self
            .get_all_bindings(true, false)
            .into_iter()
            .filter(|b| {
                b.port >= from
                    && b.port <= to
                    && (!listen_only || matches!(b.state, ConnectionState::Listen))
            })
            .collect())
    }

    fn find_all_listening(&self) -> Result<Vec<PortBinding>> {
        Ok(self
            .get_all_bindings(true, true)
            .into_iter()
            .filter(|b| matches!(b.state, ConnectionState::Listen))
            .collect())
    }

    fn find_all(&self, tcp: bool, udp: bool) -> Result<Vec<PortBinding>> {
        Ok(self.get_all_bindings(tcp, udp))
    }

    fn find_by_process_name(&self, process_name: &str) -> Result<Vec<PortBinding>> {
        let lower_name = process_name.to_lowercase();
        Ok(self
            .get_all_bindings(true, true)
            .into_iter()
            .filter(|b| b.process_name.to_lowercase().contains(&lower_name))
            .collect())
    }

    fn kill_process(&self, pid: u32, force: bool) -> Result<bool> {
        let mut args = vec!["/PID", &pid.to_string()];
        let force_flag;
        if force {
            force_flag = "/F".to_string();
            args.push(&force_flag);
        }
        let status = Command::new("taskkill").args(&args).status()?;
        Ok(status.success())
    }
}

fn normalize_address(addr: &str) -> String {
    if addr.starts_with("[::") || addr == "[::]" {
        "::".to_string()
    } else if addr == "*" {
        "0.0.0.0".to_string()
    } else {
        addr.to_string()
    }
}

fn get_command_line(pid: u32) -> String {
    Command::new("wmic")
        .args(["process", "where", &format!("ProcessId={}", pid), "get", "CommandLine", "/value"])
        .output()
        .ok()
        .and_then(|output| {
            let stdout = String::from_utf8_lossy(&output.stdout);
            for line in stdout.lines() {
                if let Some(cmd) = line.strip_prefix("CommandLine=") {
                    return Some(cmd.trim().to_string());
                }
            }
            None
        })
        .unwrap_or_default()
}
