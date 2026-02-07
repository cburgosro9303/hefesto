use anyhow::Result;
use chrono::{DateTime, Utc};
use hefesto_domain::procwatch::process_sample::{
    CpuMetrics, IoMetrics, MemoryMetrics, ProcessSample, ProcessState,
};
use sysinfo::{Pid, Process, ProcessStatus, System};

/// Trait for sampling process information.
pub trait ProcessSampler: Send + Sync {
    /// Returns all running processes.
    fn get_all_processes(&self) -> Result<Vec<ProcessSample>>;

    /// Returns a specific process by PID.
    fn sample_by_pid(&self, pid: u32) -> Result<Option<ProcessSample>>;

    /// Returns processes matching a name pattern (case-insensitive).
    fn sample_by_name(&self, name: &str) -> Result<Vec<ProcessSample>>;

    /// Returns top N processes sorted by CPU usage.
    fn top_by_cpu(&self, limit: usize) -> Result<Vec<ProcessSample>>;

    /// Returns top N processes sorted by memory usage.
    fn top_by_memory(&self, limit: usize) -> Result<Vec<ProcessSample>>;
}

/// Cross-platform process sampler using the `sysinfo` crate.
pub struct SysInfoSampler {
    system: std::sync::Mutex<System>,
}

impl SysInfoSampler {
    pub fn new() -> Self {
        let mut system = System::new_all();
        system.refresh_all();
        Self {
            system: std::sync::Mutex::new(system),
        }
    }

    fn refresh_and_collect(&self) -> Vec<ProcessSample> {
        let mut sys = self.system.lock().unwrap();
        sys.refresh_all();
        sys.processes()
            .iter()
            .map(|(pid, process)| convert_process(*pid, process))
            .collect()
    }
}

impl Default for SysInfoSampler {
    fn default() -> Self {
        Self::new()
    }
}

impl ProcessSampler for SysInfoSampler {
    fn get_all_processes(&self) -> Result<Vec<ProcessSample>> {
        Ok(self.refresh_and_collect())
    }

    fn sample_by_pid(&self, pid: u32) -> Result<Option<ProcessSample>> {
        let mut sys = self.system.lock().unwrap();
        sys.refresh_all();
        let sysinfo_pid = Pid::from_u32(pid);
        Ok(sys
            .process(sysinfo_pid)
            .map(|p| convert_process(sysinfo_pid, p)))
    }

    fn sample_by_name(&self, name: &str) -> Result<Vec<ProcessSample>> {
        let lower_name = name.to_lowercase();
        let all = self.refresh_and_collect();
        Ok(all
            .into_iter()
            .filter(|p| p.name.to_lowercase().contains(&lower_name))
            .collect())
    }

    fn top_by_cpu(&self, limit: usize) -> Result<Vec<ProcessSample>> {
        let mut all = self.refresh_and_collect();
        all.sort_by(|a, b| b.cpu.percent_instant.partial_cmp(&a.cpu.percent_instant).unwrap());
        all.truncate(limit);
        Ok(all)
    }

    fn top_by_memory(&self, limit: usize) -> Result<Vec<ProcessSample>> {
        let mut all = self.refresh_and_collect();
        all.sort_by(|a, b| b.memory.rss_bytes.cmp(&a.memory.rss_bytes));
        all.truncate(limit);
        Ok(all)
    }
}

fn convert_process(pid: Pid, process: &Process) -> ProcessSample {
    let state = match process.status() {
        ProcessStatus::Run => ProcessState::Running,
        ProcessStatus::Sleep => ProcessState::Sleeping,
        ProcessStatus::Stop => ProcessState::Stopped,
        ProcessStatus::Zombie => ProcessState::Zombie,
        ProcessStatus::Idle => ProcessState::Idle,
        _ => ProcessState::Unknown,
    };

    let start_time = if process.start_time() > 0 {
        DateTime::from_timestamp(process.start_time() as i64, 0)
    } else {
        None
    };

    let cmd_line = process
        .cmd()
        .iter()
        .map(|s| s.to_string_lossy())
        .collect::<Vec<_>>()
        .join(" ");

    ProcessSample {
        pid: pid.as_u32(),
        name: process.name().to_string_lossy().to_string(),
        command_line: cmd_line,
        user: process
            .user_id()
            .map(|uid| uid.to_string())
            .unwrap_or_default(),
        state,
        cpu: CpuMetrics {
            percent_instant: process.cpu_usage() as f64,
            percent_average: 0.0,
            user_time_ms: 0,
            system_time_ms: 0,
            total_time_ms: 0,
        },
        memory: MemoryMetrics {
            rss_bytes: process.memory(),
            virtual_bytes: process.virtual_memory(),
            shared_bytes: 0,
            percent_of_total: 0.0,
        },
        io: IoMetrics {
            read_bytes: process.disk_usage().total_read_bytes,
            write_bytes: process.disk_usage().total_written_bytes,
            read_ops: 0,
            write_ops: 0,
        },
        thread_count: 0,
        open_file_descriptors: 0,
        start_time,
        sample_time: Utc::now(),
    }
}
