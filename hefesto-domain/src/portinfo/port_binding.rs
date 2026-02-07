use serde::Serialize;

/// Protocol type for port bindings.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum Protocol {
    Tcp,
    Udp,
}

impl Protocol {
    pub fn as_str(&self) -> &str {
        match self {
            Protocol::Tcp => "TCP",
            Protocol::Udp => "UDP",
        }
    }

    pub fn from_str_loose(s: &str) -> Self {
        if s.eq_ignore_ascii_case("udp") {
            Protocol::Udp
        } else {
            Protocol::Tcp
        }
    }
}

impl std::fmt::Display for Protocol {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}

/// Connection state for a port binding.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum ConnectionState {
    Listen,
    Established,
    TimeWait,
    CloseWait,
    FinWait1,
    FinWait2,
    SynSent,
    SynRecv,
    LastAck,
    Closing,
    Other(String),
}

impl ConnectionState {
    pub fn as_str(&self) -> &str {
        match self {
            ConnectionState::Listen => "LISTEN",
            ConnectionState::Established => "ESTABLISHED",
            ConnectionState::TimeWait => "TIME_WAIT",
            ConnectionState::CloseWait => "CLOSE_WAIT",
            ConnectionState::FinWait1 => "FIN_WAIT_1",
            ConnectionState::FinWait2 => "FIN_WAIT_2",
            ConnectionState::SynSent => "SYN_SENT",
            ConnectionState::SynRecv => "SYN_RECV",
            ConnectionState::LastAck => "LAST_ACK",
            ConnectionState::Closing => "CLOSING",
            ConnectionState::Other(s) => s.as_str(),
        }
    }

    pub fn from_str_loose(s: &str) -> Self {
        match s.to_uppercase().as_str() {
            "LISTEN" | "LISTENING" => ConnectionState::Listen,
            "ESTABLISHED" | "ESTAB" => ConnectionState::Established,
            "TIME_WAIT" | "TIME-WAIT" => ConnectionState::TimeWait,
            "CLOSE_WAIT" | "CLOSE-WAIT" => ConnectionState::CloseWait,
            "FIN_WAIT_1" | "FIN-WAIT-1" | "FIN_WAIT1" => ConnectionState::FinWait1,
            "FIN_WAIT_2" | "FIN-WAIT-2" | "FIN_WAIT2" => ConnectionState::FinWait2,
            "SYN_SENT" | "SYN-SENT" => ConnectionState::SynSent,
            "SYN_RECV" | "SYN-RECV" | "SYN_RECEIVED" => ConnectionState::SynRecv,
            "LAST_ACK" | "LAST-ACK" => ConnectionState::LastAck,
            "CLOSING" => ConnectionState::Closing,
            other => ConnectionState::Other(other.to_string()),
        }
    }
}

impl std::fmt::Display for ConnectionState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}

/// Represents a port binding with process information.
#[derive(Debug, Clone, Serialize)]
pub struct PortBinding {
    pub port: u16,
    pub protocol: Protocol,
    pub state: ConnectionState,
    pub pid: u32,
    pub process_name: String,
    pub command_line: String,
    pub user: String,
    pub local_address: String,
    pub remote_address: String,
    pub remote_port: u16,
}

impl PortBinding {
    /// Creates a minimal binding for LISTEN ports.
    pub fn listen(port: u16, protocol: Protocol, pid: u32, process_name: impl Into<String>) -> Self {
        Self {
            port,
            protocol,
            state: ConnectionState::Listen,
            pid,
            process_name: process_name.into(),
            command_line: String::new(),
            user: String::new(),
            local_address: "0.0.0.0".to_string(),
            remote_address: String::new(),
            remote_port: 0,
        }
    }

    /// Returns a formatted text representation.
    pub fn to_text(&self) -> String {
        let mut sb = String::new();
        sb.push_str(self.protocol.as_str());
        sb.push(' ');
        sb.push_str(&self.local_address);
        sb.push(':');
        sb.push_str(&self.port.to_string());
        sb.push(' ');
        sb.push_str(self.state.as_str());
        sb.push_str(&format!(" pid={} ", self.pid));
        sb.push_str(&self.process_name);

        if !self.command_line.is_empty() {
            let cmd = truncate(&self.command_line, 256);
            sb.push_str(&format!(" cmd=\"{cmd}\""));
        }

        if !self.user.is_empty() {
            sb.push_str(&format!(" user={}", self.user));
        }

        if !self.remote_address.is_empty() && self.remote_port > 0 {
            sb.push_str(&format!(" -> {}:{}", self.remote_address, self.remote_port));
        }

        sb
    }

    /// Returns a compact one-line representation.
    pub fn to_compact(&self) -> String {
        format!(
            "{} :{} {} {} (pid {})",
            self.protocol, self.port, self.state, self.process_name, self.pid
        )
    }
}

fn truncate(s: &str, max_length: usize) -> String {
    if s.len() <= max_length {
        s.to_string()
    } else {
        format!("{}...", &s[..max_length - 3])
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_listen_binding() {
        let b = PortBinding::listen(8080, Protocol::Tcp, 1234, "java");
        assert_eq!(b.port, 8080);
        assert_eq!(b.protocol, Protocol::Tcp);
        assert_eq!(b.state, ConnectionState::Listen);
        assert_eq!(b.pid, 1234);
    }

    #[test]
    fn test_to_compact() {
        let b = PortBinding::listen(8080, Protocol::Tcp, 1234, "java");
        assert_eq!(b.to_compact(), "TCP :8080 LISTEN java (pid 1234)");
    }

    #[test]
    fn test_to_text_minimal() {
        let b = PortBinding::listen(80, Protocol::Tcp, 1, "nginx");
        let text = b.to_text();
        assert!(text.contains("TCP"));
        assert!(text.contains(":80"));
        assert!(text.contains("LISTEN"));
        assert!(text.contains("nginx"));
    }

    #[test]
    fn test_to_text_with_remote() {
        let b = PortBinding {
            port: 443,
            protocol: Protocol::Tcp,
            state: ConnectionState::Established,
            pid: 100,
            process_name: "curl".to_string(),
            command_line: String::new(),
            user: "root".to_string(),
            local_address: "192.168.1.1".to_string(),
            remote_address: "8.8.8.8".to_string(),
            remote_port: 443,
        };
        let text = b.to_text();
        assert!(text.contains("-> 8.8.8.8:443"));
        assert!(text.contains("user=root"));
    }

    #[test]
    fn test_connection_state_from_str() {
        assert_eq!(ConnectionState::from_str_loose("LISTEN"), ConnectionState::Listen);
        assert_eq!(ConnectionState::from_str_loose("ESTABLISHED"), ConnectionState::Established);
        assert_eq!(ConnectionState::from_str_loose("ESTAB"), ConnectionState::Established);
        assert_eq!(ConnectionState::from_str_loose("TIME_WAIT"), ConnectionState::TimeWait);
    }

    #[test]
    fn test_protocol_from_str() {
        assert_eq!(Protocol::from_str_loose("tcp"), Protocol::Tcp);
        assert_eq!(Protocol::from_str_loose("UDP"), Protocol::Udp);
        assert_eq!(Protocol::from_str_loose("anything"), Protocol::Tcp);
    }
}
