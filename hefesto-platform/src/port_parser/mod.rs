use anyhow::Result;
use hefesto_domain::portinfo::port_binding::PortBinding;

#[cfg(target_os = "macos")]
mod macos;
#[cfg(target_os = "linux")]
mod linux;
#[cfg(target_os = "windows")]
mod windows;

/// Trait for parsing port information from OS commands.
pub trait PortParser: Send + Sync {
    /// Finds bindings for a specific port.
    fn find_by_port(&self, port: u16, tcp: bool, udp: bool) -> Result<Vec<PortBinding>>;

    /// Finds all ports associated with a process.
    fn find_by_pid(&self, pid: u32) -> Result<Vec<PortBinding>>;

    /// Finds ports in a range.
    fn find_in_range(&self, from: u16, to: u16, listen_only: bool) -> Result<Vec<PortBinding>>;

    /// Finds all ports currently in LISTEN state.
    fn find_all_listening(&self) -> Result<Vec<PortBinding>>;

    /// Finds all active port bindings (any state).
    fn find_all(&self, tcp: bool, udp: bool) -> Result<Vec<PortBinding>>;

    /// Finds ports by process name (case-insensitive partial match).
    fn find_by_process_name(&self, process_name: &str) -> Result<Vec<PortBinding>>;

    /// Terminates a process.
    fn kill_process(&self, pid: u32, force: bool) -> Result<bool>;

    /// Finds by port with TCP only (convenience).
    fn find_by_port_tcp(&self, port: u16) -> Result<Vec<PortBinding>> {
        self.find_by_port(port, true, false)
    }

    /// Finds all TCP and UDP bindings (convenience).
    fn find_all_both(&self) -> Result<Vec<PortBinding>> {
        self.find_all(true, true)
    }
}

/// Creates the platform-specific port parser.
pub fn create_parser() -> Box<dyn PortParser> {
    #[cfg(target_os = "macos")]
    { Box::new(macos::MacOsPortParser::new()) }

    #[cfg(target_os = "linux")]
    { Box::new(linux::LinuxPortParser::new()) }

    #[cfg(target_os = "windows")]
    { Box::new(windows::WindowsPortParser::new()) }

    #[cfg(not(any(target_os = "macos", target_os = "linux", target_os = "windows")))]
    { compile_error!("Unsupported operating system") }
}
