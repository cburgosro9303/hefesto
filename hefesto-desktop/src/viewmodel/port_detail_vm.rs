//! Port detail view model.
//!
//! Manages detailed information for a selected port binding,
//! including service info, Docker info, and security recommendations.
//! This view model is designed for future use when a detail panel
//! is added to the network explorer split view.

use hefesto_domain::portinfo::port_binding::PortBinding;

/// Holds detailed information about a selected port.
#[derive(Debug, Clone)]
pub struct PortDetail {
    pub port: u16,
    pub protocol: String,
    pub state: String,
    pub local_address: String,
    pub remote_address: String,
    pub pid: u32,
    pub process_name: String,
    pub command_line: String,
    pub user: String,
    pub is_exposed: bool,
}

impl PortDetail {
    /// Creates a PortDetail from a PortBinding.
    pub fn from_binding(binding: &PortBinding) -> Self {
        let addr = &binding.local_address;
        let is_exposed = addr == "0.0.0.0" || addr == "::" || addr == "*";

        Self {
            port: binding.port,
            protocol: binding.protocol.to_string(),
            state: binding.state.to_string(),
            local_address: binding.local_address.clone(),
            remote_address: if binding.remote_port > 0 {
                format!("{}:{}", binding.remote_address, binding.remote_port)
            } else {
                String::new()
            },
            pid: binding.pid,
            process_name: binding.process_name.clone(),
            command_line: binding.command_line.clone(),
            user: binding.user.clone(),
            is_exposed,
        }
    }

    /// Returns a security recommendation based on the binding.
    pub fn security_recommendation(&self) -> &str {
        if self.is_exposed {
            "This port is exposed to the network. Consider restricting access with a firewall rule."
        } else {
            "Port is bound to localhost only. No external exposure."
        }
    }
}
