use chrono::{DateTime, Utc};
use serde::Serialize;

/// Extended process information for a port binding.
#[derive(Debug, Clone, Serialize)]
pub struct ProcessInfo {
    pub pid: u32,
    pub name: String,
    pub command_line: String,
    pub user: String,
    pub working_directory: String,
    pub memory_rss_kb: u64,
    pub memory_virtual_kb: u64,
    pub cpu_time_ms: u64,
    pub thread_count: u32,
    pub start_time: Option<DateTime<Utc>>,
}

impl ProcessInfo {
    /// Returns the process uptime.
    pub fn uptime(&self) -> chrono::Duration {
        match self.start_time {
            Some(start) => Utc::now() - start,
            None => chrono::Duration::zero(),
        }
    }

    /// Returns memory RSS in human-readable format.
    pub fn memory_rss_formatted(&self) -> String {
        format_memory(self.memory_rss_kb)
    }

    /// Returns virtual memory in human-readable format.
    pub fn memory_virtual_formatted(&self) -> String {
        format_memory(self.memory_virtual_kb)
    }

    /// Returns CPU time in human-readable format.
    pub fn cpu_time_formatted(&self) -> String {
        let seconds = self.cpu_time_ms / 1000;
        let minutes = seconds / 60;
        let hours = minutes / 60;

        if hours > 0 {
            format!("{}h{:02}m{:02}s", hours, minutes % 60, seconds % 60)
        } else if minutes > 0 {
            format!("{}m{:02}s", minutes, seconds % 60)
        } else {
            format!("{}s", seconds)
        }
    }

    /// Returns uptime in human-readable format.
    pub fn uptime_formatted(&self) -> String {
        let uptime = self.uptime();
        let days = uptime.num_days();
        let hours = uptime.num_hours() % 24;
        let minutes = uptime.num_minutes() % 60;

        if days > 0 {
            format!("{}d {}h {}m", days, hours, minutes)
        } else if hours > 0 {
            format!("{}h {}m", hours, minutes)
        } else {
            format!("{}m", minutes)
        }
    }

    /// Creates a minimal ProcessInfo with basic data.
    pub fn basic(pid: u32, name: impl Into<String>, user: impl Into<String>) -> Self {
        Self {
            pid,
            name: name.into(),
            command_line: String::new(),
            user: user.into(),
            working_directory: String::new(),
            memory_rss_kb: 0,
            memory_virtual_kb: 0,
            cpu_time_ms: 0,
            thread_count: 0,
            start_time: None,
        }
    }
}

fn format_memory(kb: u64) -> String {
    if kb < 1024 {
        format!("{} KB", kb)
    } else if kb < 1024 * 1024 {
        format!("{:.1} MB", kb as f64 / 1024.0)
    } else {
        format!("{:.2} GB", kb as f64 / (1024.0 * 1024.0))
    }
}
