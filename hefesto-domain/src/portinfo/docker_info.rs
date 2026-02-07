use serde::Serialize;

/// Represents a Docker port mapping.
#[derive(Debug, Clone, Serialize)]
pub struct PortMapping {
    pub host_port: u16,
    pub container_port: u16,
    pub protocol: String,
    pub host_ip: String,
}

impl PortMapping {
    /// Returns formatted string: hostIp:hostPort->containerPort/protocol
    pub fn to_display_string(&self) -> String {
        let host = if self.host_ip.is_empty() || self.host_ip == "0.0.0.0" {
            String::new()
        } else {
            format!("{}:", self.host_ip)
        };
        format!(
            "{}{}->{}{}",
            host,
            self.host_port,
            self.container_port,
            format!("/{}", self.protocol.to_lowercase())
        )
    }
}

/// Docker container information.
#[derive(Debug, Clone, Serialize)]
pub struct DockerInfo {
    pub container_id: String,
    pub container_name: String,
    pub image: String,
    pub status: String,
    pub port_mappings: Vec<PortMapping>,
}

impl DockerInfo {
    /// Returns short container ID (first 12 characters).
    pub fn short_id(&self) -> &str {
        if self.container_id.len() <= 12 {
            &self.container_id
        } else {
            &self.container_id[..12]
        }
    }

    /// Checks if container is running.
    pub fn is_running(&self) -> bool {
        self.status.to_lowercase().contains("up")
    }

    /// Returns all port mappings as a formatted string.
    pub fn port_mappings_formatted(&self) -> String {
        if self.port_mappings.is_empty() {
            return String::new();
        }
        self.port_mappings
            .iter()
            .map(|pm| pm.to_display_string())
            .collect::<Vec<_>>()
            .join(", ")
    }

    /// Creates a DockerInfo with minimal data.
    pub fn simple(
        container_id: impl Into<String>,
        container_name: impl Into<String>,
        image: impl Into<String>,
        status: impl Into<String>,
    ) -> Self {
        Self {
            container_id: container_id.into(),
            container_name: container_name.into(),
            image: image.into(),
            status: status.into(),
            port_mappings: Vec::new(),
        }
    }
}
