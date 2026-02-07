use chrono::{DateTime, Utc};
use serde::Serialize;

/// Process state enumeration.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum ProcessState {
    Running,
    Sleeping,
    Waiting,
    Zombie,
    Stopped,
    Idle,
    Unknown,
}

impl ProcessState {
    pub fn code(&self) -> &str {
        match self {
            ProcessState::Running => "R",
            ProcessState::Sleeping => "S",
            ProcessState::Waiting => "D",
            ProcessState::Zombie => "Z",
            ProcessState::Stopped => "T",
            ProcessState::Idle => "I",
            ProcessState::Unknown => "?",
        }
    }

    pub fn description(&self) -> &str {
        match self {
            ProcessState::Running => "Running",
            ProcessState::Sleeping => "Sleeping",
            ProcessState::Waiting => "Waiting",
            ProcessState::Zombie => "Zombie",
            ProcessState::Stopped => "Stopped",
            ProcessState::Idle => "Idle",
            ProcessState::Unknown => "Unknown",
        }
    }

    pub fn from_code(code: &str) -> Self {
        if code.is_empty() {
            return ProcessState::Unknown;
        }
        match code.chars().next().unwrap() {
            'R' => ProcessState::Running,
            'S' | 's' => ProcessState::Sleeping,
            'D' => ProcessState::Waiting,
            'Z' => ProcessState::Zombie,
            'T' | 't' => ProcessState::Stopped,
            'I' => ProcessState::Idle,
            _ => ProcessState::Unknown,
        }
    }
}

impl std::fmt::Display for ProcessState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.description())
    }
}

/// CPU metrics.
#[derive(Debug, Clone, Serialize)]
pub struct CpuMetrics {
    pub percent_instant: f64,
    pub percent_average: f64,
    pub user_time_ms: u64,
    pub system_time_ms: u64,
    pub total_time_ms: u64,
}

impl CpuMetrics {
    pub fn zero() -> Self {
        Self {
            percent_instant: 0.0,
            percent_average: 0.0,
            user_time_ms: 0,
            system_time_ms: 0,
            total_time_ms: 0,
        }
    }

    pub fn percent_formatted(&self) -> String {
        format!("{:.1}%", self.percent_instant)
    }

    pub fn average_formatted(&self) -> String {
        format!("{:.1}%", self.percent_average)
    }
}

/// Memory metrics.
#[derive(Debug, Clone, Serialize)]
pub struct MemoryMetrics {
    pub rss_bytes: u64,
    pub virtual_bytes: u64,
    pub shared_bytes: u64,
    pub percent_of_total: f64,
}

impl MemoryMetrics {
    pub fn zero() -> Self {
        Self {
            rss_bytes: 0,
            virtual_bytes: 0,
            shared_bytes: 0,
            percent_of_total: 0.0,
        }
    }

    pub fn rss_formatted(&self) -> String {
        format_bytes(self.rss_bytes)
    }

    pub fn virtual_formatted(&self) -> String {
        format_bytes(self.virtual_bytes)
    }

    pub fn rss_mb(&self) -> u64 {
        self.rss_bytes / (1024 * 1024)
    }

    pub fn rss_gb(&self) -> u64 {
        self.rss_bytes / (1024 * 1024 * 1024)
    }
}

/// I/O metrics.
#[derive(Debug, Clone, Serialize)]
pub struct IoMetrics {
    pub read_bytes: u64,
    pub write_bytes: u64,
    pub read_ops: u64,
    pub write_ops: u64,
}

impl IoMetrics {
    pub fn zero() -> Self {
        Self {
            read_bytes: 0,
            write_bytes: 0,
            read_ops: 0,
            write_ops: 0,
        }
    }

    pub fn read_formatted(&self) -> String {
        format_bytes(self.read_bytes)
    }

    pub fn write_formatted(&self) -> String {
        format_bytes(self.write_bytes)
    }
}

fn format_bytes(bytes: u64) -> String {
    if bytes < 1024 {
        format!("{} B", bytes)
    } else if bytes < 1024 * 1024 {
        format!("{:.1} KB", bytes as f64 / 1024.0)
    } else if bytes < 1024 * 1024 * 1024 {
        format!("{:.1} MB", bytes as f64 / (1024.0 * 1024.0))
    } else {
        format!("{:.2} GB", bytes as f64 / (1024.0 * 1024.0 * 1024.0))
    }
}

/// A single sample of process metrics at a point in time.
#[derive(Debug, Clone, Serialize)]
pub struct ProcessSample {
    pub pid: u32,
    pub name: String,
    pub command_line: String,
    pub user: String,
    pub state: ProcessState,
    pub cpu: CpuMetrics,
    pub memory: MemoryMetrics,
    pub io: IoMetrics,
    pub thread_count: u32,
    pub open_file_descriptors: u32,
    pub start_time: Option<DateTime<Utc>>,
    pub sample_time: DateTime<Utc>,
}

impl ProcessSample {
    /// Returns process uptime.
    pub fn uptime(&self) -> chrono::Duration {
        match self.start_time {
            Some(start) => Utc::now() - start,
            None => chrono::Duration::zero(),
        }
    }

    /// Returns formatted uptime.
    pub fn uptime_formatted(&self) -> String {
        let duration = self.uptime();
        let days = duration.num_days();
        let hours = duration.num_hours() % 24;
        let minutes = duration.num_minutes() % 60;
        let seconds = duration.num_seconds() % 60;

        if days > 0 {
            format!("{}d {}h {}m", days, hours, minutes)
        } else if hours > 0 {
            format!("{}h {}m {}s", hours, minutes, seconds)
        } else if minutes > 0 {
            format!("{}m {}s", minutes, seconds)
        } else {
            format!("{}s", seconds)
        }
    }

    /// Creates a minimal sample for a process.
    pub fn minimal(pid: u32, name: impl Into<String>, user: impl Into<String>) -> Self {
        Self {
            pid,
            name: name.into(),
            command_line: String::new(),
            user: user.into(),
            state: ProcessState::Unknown,
            cpu: CpuMetrics::zero(),
            memory: MemoryMetrics::zero(),
            io: IoMetrics::zero(),
            thread_count: 0,
            open_file_descriptors: 0,
            start_time: None,
            sample_time: Utc::now(),
        }
    }
}
