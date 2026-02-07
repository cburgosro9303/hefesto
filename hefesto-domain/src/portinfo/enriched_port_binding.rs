use serde::Serialize;

use super::docker_info::DockerInfo;
use super::port_binding::{ConnectionState, PortBinding, Protocol};
use super::process_info::ProcessInfo;
use super::service_info::ServiceInfo;

/// A port binding enriched with additional context.
#[derive(Debug, Clone, Serialize)]
pub struct EnrichedPortBinding {
    pub binding: PortBinding,
    pub service_info: Option<ServiceInfo>,
    pub process_info: Option<ProcessInfo>,
    pub docker_info: Option<DockerInfo>,
}

impl EnrichedPortBinding {
    /// Creates an enriched binding from a basic binding.
    pub fn from_binding(binding: PortBinding) -> Self {
        Self {
            binding,
            service_info: None,
            process_info: None,
            docker_info: None,
        }
    }

    /// Creates an enriched binding with service info.
    pub fn with_service(binding: PortBinding, service_info: ServiceInfo) -> Self {
        Self {
            binding,
            service_info: Some(service_info),
            process_info: None,
            docker_info: None,
        }
    }

    /// Returns a copy with service info.
    pub fn set_service_info(mut self, info: ServiceInfo) -> Self {
        self.service_info = Some(info);
        self
    }

    /// Returns a copy with process info.
    pub fn set_process_info(mut self, info: ProcessInfo) -> Self {
        self.process_info = Some(info);
        self
    }

    /// Returns a copy with Docker info.
    pub fn set_docker_info(mut self, info: DockerInfo) -> Self {
        self.docker_info = Some(info);
        self
    }

    // Delegate methods to binding
    pub fn port(&self) -> u16 {
        self.binding.port
    }

    pub fn protocol(&self) -> &Protocol {
        &self.binding.protocol
    }

    pub fn state(&self) -> &ConnectionState {
        &self.binding.state
    }

    pub fn pid(&self) -> u32 {
        self.binding.pid
    }

    pub fn process_name(&self) -> &str {
        &self.binding.process_name
    }

    pub fn local_address(&self) -> &str {
        &self.binding.local_address
    }

    pub fn user(&self) -> &str {
        &self.binding.user
    }

    /// Checks if the port is exposed to the network (0.0.0.0 or ::).
    pub fn is_exposed(&self) -> bool {
        let addr = &self.binding.local_address;
        addr == "0.0.0.0" || addr == "::" || addr == "*"
    }

    /// Checks if bound to localhost only.
    pub fn is_local_only(&self) -> bool {
        let addr = &self.binding.local_address;
        addr == "127.0.0.1" || addr == "::1" || addr == "localhost"
    }

    /// Checks if this is a Docker container.
    pub fn is_docker(&self) -> bool {
        self.docker_info.is_some()
    }

    /// Returns a formatted text representation.
    pub fn to_text(&self) -> String {
        let mut sb = self.binding.to_text();
        if let Some(ref si) = self.service_info {
            sb.push(' ');
            sb.push_str(&si.to_tag());
        }
        sb
    }

    /// Returns a compact representation with service tag.
    pub fn to_compact_with_service(&self) -> String {
        let base = self.binding.to_compact();
        if let Some(ref si) = self.service_info {
            format!("{} {}", base, si.to_tag())
        } else {
            base
        }
    }
}
