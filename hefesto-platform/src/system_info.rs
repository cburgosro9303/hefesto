/// Operating system type.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum OperatingSystem {
    MacOs,
    Linux,
    Windows,
    Unknown,
}

impl OperatingSystem {
    pub fn display_name(&self) -> &str {
        match self {
            OperatingSystem::MacOs => "macOS",
            OperatingSystem::Linux => "Linux",
            OperatingSystem::Windows => "Windows",
            OperatingSystem::Unknown => "Unknown",
        }
    }
}

impl std::fmt::Display for OperatingSystem {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.display_name())
    }
}

/// System information.
#[derive(Debug, Clone)]
pub struct SystemInfo {
    pub os: OperatingSystem,
    pub os_version: String,
    pub hostname: String,
    pub cpu_count: usize,
    pub total_memory_bytes: u64,
}

/// Returns the current operating system.
pub fn current_os() -> OperatingSystem {
    if cfg!(target_os = "macos") {
        OperatingSystem::MacOs
    } else if cfg!(target_os = "linux") {
        OperatingSystem::Linux
    } else if cfg!(target_os = "windows") {
        OperatingSystem::Windows
    } else {
        OperatingSystem::Unknown
    }
}

/// Gathers current system information.
pub fn get_system_info() -> SystemInfo {
    use sysinfo::System;

    let mut sys = System::new_all();
    sys.refresh_all();

    SystemInfo {
        os: current_os(),
        os_version: System::os_version().unwrap_or_default(),
        hostname: System::host_name().unwrap_or_default(),
        cpu_count: sys.cpus().len(),
        total_memory_bytes: sys.total_memory(),
    }
}
