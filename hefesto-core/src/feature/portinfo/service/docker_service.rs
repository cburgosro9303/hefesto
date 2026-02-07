use std::process::Command;
use std::sync::OnceLock;

use hefesto_domain::portinfo::docker_info::{DockerInfo, PortMapping};
use hefesto_domain::portinfo::port_binding::PortBinding;

/// Cached Docker availability check.
static DOCKER_AVAILABLE: OnceLock<bool> = OnceLock::new();

/// Service for Docker container integration and port mapping detection.
///
/// Provides methods to detect running Docker containers, retrieve their port
/// mappings, and correlate them with system-level port bindings. Falls back
/// gracefully when Docker is not installed or the daemon is not running.
pub struct DockerService;

impl DockerService {
    /// Creates a new `DockerService`.
    pub fn new() -> Self {
        Self
    }

    /// Checks if Docker is available on the system.
    pub fn is_docker_available(&self) -> bool {
        *DOCKER_AVAILABLE.get_or_init(Self::check_docker_available)
    }

    /// Gets Docker info for a container by PID.
    pub fn get_container_by_pid(&self, pid: u32) -> Option<DockerInfo> {
        if !self.is_docker_available() {
            return None;
        }

        let container_id = self.find_container_id_by_pid(pid)?;
        self.get_container_info(&container_id)
    }

    /// Gets Docker info for a specific container by ID or name.
    pub fn get_container_info(&self, container_id_or_name: &str) -> Option<DockerInfo> {
        if !self.is_docker_available() {
            return None;
        }

        let output = Command::new("docker")
            .args([
                "inspect",
                "--format",
                "{{.Id}}|{{.Name}}|{{.Config.Image}}|{{.State.Status}}",
                container_id_or_name,
            ])
            .output()
            .ok()?;

        let line = String::from_utf8_lossy(&output.stdout);
        let line = line.trim();

        if line.is_empty() || line.contains("Error") {
            return None;
        }

        let parts: Vec<&str> = line.split('|').collect();
        if parts.len() < 4 {
            return None;
        }

        let container_id = parts[0].to_string();
        let name = parts[1].strip_prefix('/').unwrap_or(parts[1]).to_string();
        let image = parts[2].to_string();
        let status = parts[3].to_string();
        let port_mappings = self.get_port_mappings(container_id_or_name);

        Some(DockerInfo {
            container_id,
            container_name: name,
            image,
            status,
            port_mappings,
        })
    }

    /// Gets all running containers with their port bindings.
    pub fn list_running_containers(&self) -> Vec<DockerInfo> {
        if !self.is_docker_available() {
            return Vec::new();
        }

        let output = match Command::new("docker")
            .args(["ps", "--format", "{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}"])
            .output()
        {
            Ok(o) => o,
            Err(_) => return Vec::new(),
        };

        let stdout = String::from_utf8_lossy(&output.stdout);
        let mut containers = Vec::new();

        for line in stdout.lines() {
            let line = line.trim();
            if line.is_empty() {
                continue;
            }

            let parts: Vec<&str> = line.split('|').collect();
            if parts.len() < 4 {
                continue;
            }

            let container_id = parts[0].to_string();
            let name = parts[1].to_string();
            let image = parts[2].to_string();
            let status = parts[3].to_string();
            let port_mappings = self.get_port_mappings(&container_id);

            containers.push(DockerInfo {
                container_id,
                container_name: name,
                image,
                status,
                port_mappings,
            });
        }

        containers
    }

    /// Gets port mappings for a container.
    pub fn get_port_mappings(&self, container_id_or_name: &str) -> Vec<PortMapping> {
        let output = match Command::new("docker")
            .args(["port", container_id_or_name])
            .output()
        {
            Ok(o) => o,
            Err(_) => return Vec::new(),
        };

        let stdout = String::from_utf8_lossy(&output.stdout);
        let mut mappings = Vec::new();

        for line in stdout.lines() {
            if let Some(mapping) = Self::parse_port_line(line) {
                mappings.push(mapping);
            }
        }

        mappings
    }

    /// Checks if a port binding is from a Docker container.
    pub fn is_docker_binding(&self, binding: &PortBinding) -> bool {
        if !self.is_docker_available() {
            return false;
        }

        // Check if process name suggests Docker
        let name = binding.process_name.to_lowercase();
        if name.contains("docker") || name.contains("containerd") {
            return true;
        }

        // Check by PID
        self.find_container_id_by_pid(binding.pid).is_some()
    }

    /// Finds which container is using a specific host port.
    pub fn find_container_by_host_port(&self, port: u16) -> Option<DockerInfo> {
        let containers = self.list_running_containers();

        for container in containers {
            for mapping in &container.port_mappings {
                if mapping.host_port == port {
                    return Some(container);
                }
            }
        }

        None
    }

    // ── Private helpers ────────────────────────────────────────────────────

    fn check_docker_available() -> bool {
        Command::new("docker")
            .arg("info")
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status()
            .map(|s| s.success())
            .unwrap_or(false)
    }

    /// Parses a Docker port output line.
    /// Format: `80/tcp -> 0.0.0.0:8080` or `80/tcp -> [::]:8080`
    fn parse_port_line(line: &str) -> Option<PortMapping> {
        // Expected format: "80/tcp -> 0.0.0.0:8080"
        let parts: Vec<&str> = line.split("->").collect();
        if parts.len() != 2 {
            return None;
        }

        let container_part = parts[0].trim();
        let host_part = parts[1].trim();

        // Parse container port and protocol: "80/tcp"
        let container_parts: Vec<&str> = container_part.split('/').collect();
        if container_parts.len() != 2 {
            return None;
        }

        let container_port: u16 = container_parts[0].parse().ok()?;
        let protocol = container_parts[1].to_string();

        // Parse host address and port: "0.0.0.0:8080" or "[::]:8080"
        let host_part_clean = host_part.replace(['[', ']'], "");
        let last_colon = host_part_clean.rfind(':')?;

        let host_ip = host_part_clean[..last_colon].to_string();
        let host_port: u16 = host_part_clean[last_colon + 1..].parse().ok()?;

        Some(PortMapping {
            host_port,
            container_port,
            protocol,
            host_ip,
        })
    }

    fn find_container_id_by_pid(&self, pid: u32) -> Option<String> {
        // Method 1: Check cgroup (Linux)
        if let Some(id) = self.find_container_id_from_cgroup(pid) {
            return Some(id);
        }

        // Method 2: Check all containers' top
        self.find_container_id_from_docker_top(pid)
    }

    fn find_container_id_from_cgroup(&self, pid: u32) -> Option<String> {
        let cgroup_path = format!("/proc/{pid}/cgroup");
        let output = Command::new("cat")
            .arg(&cgroup_path)
            .output()
            .ok()?;

        let stdout = String::from_utf8_lossy(&output.stdout);
        let re = regex::Regex::new(r"docker[/-]([a-f0-9]{64})").ok()?;

        for line in stdout.lines() {
            if let Some(captures) = re.captures(line) {
                if let Some(id) = captures.get(1) {
                    return Some(id.as_str().to_string());
                }
            }
        }

        None
    }

    fn find_container_id_from_docker_top(&self, pid: u32) -> Option<String> {
        let output = Command::new("docker")
            .args(["ps", "-q"])
            .output()
            .ok()?;

        let stdout = String::from_utf8_lossy(&output.stdout);
        let container_ids: Vec<&str> = stdout.lines().map(|l| l.trim()).filter(|l| !l.is_empty()).collect();

        for container_id in container_ids {
            if self.container_has_pid(container_id, pid) {
                return Some(container_id.to_string());
            }
        }

        None
    }

    fn container_has_pid(&self, container_id: &str, pid: u32) -> bool {
        let output = match Command::new("docker")
            .args(["top", container_id, "-o", "pid"])
            .output()
        {
            Ok(o) => o,
            Err(_) => return false,
        };

        let stdout = String::from_utf8_lossy(&output.stdout);
        for line in stdout.lines() {
            if let Ok(line_pid) = line.trim().parse::<u32>() {
                if line_pid == pid {
                    return true;
                }
            }
        }

        false
    }
}

impl Default for DockerService {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_port_line_standard() {
        let mapping = DockerService::parse_port_line("80/tcp -> 0.0.0.0:8080");
        assert!(mapping.is_some());
        let m = mapping.unwrap();
        assert_eq!(m.host_port, 8080);
        assert_eq!(m.container_port, 80);
        assert_eq!(m.protocol, "tcp");
        assert_eq!(m.host_ip, "0.0.0.0");
    }

    #[test]
    fn test_parse_port_line_ipv6() {
        let mapping = DockerService::parse_port_line("80/tcp -> [::]:8080");
        assert!(mapping.is_some());
        let m = mapping.unwrap();
        assert_eq!(m.host_port, 8080);
        assert_eq!(m.host_ip, "::");
    }

    #[test]
    fn test_parse_port_line_invalid() {
        assert!(DockerService::parse_port_line("invalid").is_none());
        assert!(DockerService::parse_port_line("").is_none());
    }

    #[test]
    fn test_parse_port_line_udp() {
        let mapping = DockerService::parse_port_line("53/udp -> 0.0.0.0:5353");
        assert!(mapping.is_some());
        let m = mapping.unwrap();
        assert_eq!(m.protocol, "udp");
        assert_eq!(m.container_port, 53);
        assert_eq!(m.host_port, 5353);
    }
}
