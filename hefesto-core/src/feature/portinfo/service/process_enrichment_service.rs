use std::process::Command;

use hefesto_domain::portinfo::process_info::ProcessInfo;

/// Service for enriching process information with extended details.
///
/// Gathers additional process metadata (memory usage, CPU time, thread count,
/// working directory, full command line) using platform-specific commands.
pub struct ProcessEnrichmentService;

impl ProcessEnrichmentService {
    /// Creates a new `ProcessEnrichmentService`.
    pub fn new() -> Self {
        Self
    }

    /// Gets extended process information for a PID.
    pub fn get_process_info(&self, pid: u32) -> Option<ProcessInfo> {
        #[cfg(target_os = "linux")]
        {
            self.get_linux_process_info(pid)
        }

        #[cfg(target_os = "macos")]
        {
            self.get_macos_process_info(pid)
        }

        #[cfg(target_os = "windows")]
        {
            self.get_windows_process_info(pid)
        }

        #[cfg(not(any(target_os = "linux", target_os = "macos", target_os = "windows")))]
        {
            None
        }
    }

    // ── Linux ──────────────────────────────────────────────────────────────

    #[cfg(target_os = "linux")]
    fn get_linux_process_info(&self, pid: u32) -> Option<ProcessInfo> {
        let output = Command::new("ps")
            .args([
                "-p",
                &pid.to_string(),
                "-o",
                "comm=,user=,rss=,vsz=,time=,nlwp=",
            ])
            .output()
            .ok()?;

        let line = String::from_utf8_lossy(&output.stdout);
        let line = line.trim();
        if line.is_empty() {
            return None;
        }

        self.parse_linux_ps_output(pid, line)
    }

    #[cfg(target_os = "linux")]
    fn parse_linux_ps_output(&self, pid: u32, line: &str) -> Option<ProcessInfo> {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() < 6 {
            return None;
        }

        let name = parts[0].to_string();
        let user = parts[1].to_string();
        let rss_kb: u64 = parts[2].parse().ok()?;
        let vsz_kb: u64 = parts[3].parse().ok()?;
        let cpu_time_ms = parse_cpu_time(parts[4]);
        let thread_count: u32 = parts[5].parse().unwrap_or(0);

        let cwd = self.get_working_directory(pid);
        let cmd_line = self.get_command_line(pid);

        Some(ProcessInfo {
            pid,
            name,
            command_line: cmd_line,
            user,
            working_directory: cwd,
            memory_rss_kb: rss_kb,
            memory_virtual_kb: vsz_kb,
            cpu_time_ms,
            thread_count,
            start_time: None,
        })
    }

    // ── macOS ──────────────────────────────────────────────────────────────

    #[cfg(target_os = "macos")]
    fn get_macos_process_info(&self, pid: u32) -> Option<ProcessInfo> {
        let output = Command::new("ps")
            .args(["-p", &pid.to_string(), "-o", "comm=,user=,rss=,vsz=,time="])
            .output()
            .ok()?;

        let line = String::from_utf8_lossy(&output.stdout);
        let line = line.trim();
        if line.is_empty() {
            return None;
        }

        self.parse_macos_ps_output(pid, line)
    }

    #[cfg(target_os = "macos")]
    fn parse_macos_ps_output(&self, pid: u32, line: &str) -> Option<ProcessInfo> {
        // Format: name user rss vsz time
        let re = regex::Regex::new(r"^(\S+)\s+(\S+)\s+(\d+)\s+(\d+)\s+(\S+)").ok()?;
        let captures = re.captures(line.trim())?;

        let name = captures.get(1)?.as_str().to_string();
        let user = captures.get(2)?.as_str().to_string();
        let rss_kb: u64 = captures.get(3)?.as_str().parse().ok()?;
        let vsz_kb: u64 = captures.get(4)?.as_str().parse().ok()?;
        let cpu_time_ms = parse_cpu_time(captures.get(5)?.as_str());

        let cwd = self.get_working_directory(pid);
        let cmd_line = self.get_command_line(pid);
        let thread_count = self.get_thread_count(pid);

        Some(ProcessInfo {
            pid,
            name,
            command_line: cmd_line,
            user,
            working_directory: cwd,
            memory_rss_kb: rss_kb,
            memory_virtual_kb: vsz_kb,
            cpu_time_ms,
            thread_count,
            start_time: None,
        })
    }

    // ── Windows ────────────────────────────────────────────────────────────

    #[cfg(target_os = "windows")]
    fn get_windows_process_info(&self, pid: u32) -> Option<ProcessInfo> {
        let output = Command::new("wmic")
            .args([
                "process",
                "where",
                &format!("ProcessId={pid}"),
                "get",
                "Name,CommandLine,WorkingSetSize,VirtualSize,UserModeTime,KernelModeTime,ThreadCount,CreationDate",
                "/format:csv",
            ])
            .output()
            .ok()?;

        let stdout = String::from_utf8_lossy(&output.stdout);
        let lines: Vec<&str> = stdout.lines().filter(|l| !l.trim().is_empty()).collect();

        // Skip header lines
        if lines.len() < 3 {
            return None;
        }

        self.parse_windows_wmic_output(pid, lines[2])
    }

    #[cfg(target_os = "windows")]
    fn parse_windows_wmic_output(&self, pid: u32, line: &str) -> Option<ProcessInfo> {
        let parts: Vec<&str> = line.split(',').collect();
        if parts.len() < 9 {
            return None;
        }

        let cmd_line = parts[1].to_string();
        let name = parts[4].to_string();
        let thread_count: u32 = parts[5].trim().parse().unwrap_or(0);
        let user_mode_time: u64 = parts[6].trim().parse().unwrap_or(0);
        let kernel_mode_time: u64 = parts[3].trim().parse().unwrap_or(0);
        let vsz_bytes: u64 = parts[7].trim().parse().unwrap_or(0);
        let rss_bytes: u64 = parts[8].trim().parse().unwrap_or(0);

        let cpu_time_ms = (user_mode_time + kernel_mode_time) / 10_000; // 100ns to ms
        let rss_kb = rss_bytes / 1024;
        let vsz_kb = vsz_bytes / 1024;

        Some(ProcessInfo {
            pid,
            name,
            command_line: cmd_line,
            user: String::new(),
            working_directory: String::new(),
            memory_rss_kb: rss_kb,
            memory_virtual_kb: vsz_kb,
            cpu_time_ms,
            thread_count,
            start_time: None,
        })
    }

    // ── Cross-platform helpers ─────────────────────────────────────────────

    fn get_working_directory(&self, pid: u32) -> String {
        #[cfg(target_os = "linux")]
        {
            Command::new("readlink")
                .args(["-f", &format!("/proc/{pid}/cwd")])
                .output()
                .ok()
                .and_then(|o| {
                    let s = String::from_utf8_lossy(&o.stdout).trim().to_string();
                    if s.is_empty() { None } else { Some(s) }
                })
                .unwrap_or_default()
        }

        #[cfg(target_os = "macos")]
        {
            Command::new("lsof")
                .args(["-p", &pid.to_string(), "-Fn"])
                .output()
                .ok()
                .and_then(|o| {
                    let stdout = String::from_utf8_lossy(&o.stdout);
                    for line in stdout.lines() {
                        if line.starts_with('n') && line.contains("cwd") {
                            return Some(line[1..].to_string());
                        }
                    }
                    None
                })
                .unwrap_or_default()
        }

        #[cfg(target_os = "windows")]
        {
            String::new()
        }

        #[cfg(not(any(target_os = "linux", target_os = "macos", target_os = "windows")))]
        {
            String::new()
        }
    }

    fn get_command_line(&self, pid: u32) -> String {
        #[cfg(target_os = "linux")]
        {
            Command::new("cat")
                .arg(format!("/proc/{pid}/cmdline"))
                .output()
                .ok()
                .map(|o| {
                    String::from_utf8_lossy(&o.stdout)
                        .replace('\0', " ")
                        .trim()
                        .to_string()
                })
                .unwrap_or_default()
        }

        #[cfg(target_os = "macos")]
        {
            Command::new("ps")
                .args(["-p", &pid.to_string(), "-o", "command="])
                .output()
                .ok()
                .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
                .unwrap_or_default()
        }

        #[cfg(target_os = "windows")]
        {
            String::new()
        }

        #[cfg(not(any(target_os = "linux", target_os = "macos", target_os = "windows")))]
        {
            String::new()
        }
    }

    #[cfg(target_os = "macos")]
    fn get_thread_count(&self, pid: u32) -> u32 {
        Command::new("ps")
            .args(["-M", "-p", &pid.to_string()])
            .output()
            .ok()
            .map(|o| {
                let count = String::from_utf8_lossy(&o.stdout).lines().count();
                // Subtract header line
                count.saturating_sub(1) as u32
            })
            .unwrap_or(0)
    }
}

impl Default for ProcessEnrichmentService {
    fn default() -> Self {
        Self::new()
    }
}

/// Parses a CPU time string in the format HH:MM:SS or MM:SS into milliseconds.
fn parse_cpu_time(time: &str) -> u64 {
    let parts: Vec<&str> = time.split(':').collect();
    match parts.len() {
        3 => {
            let hours: u64 = parts[0].parse().unwrap_or(0);
            let minutes: u64 = parts[1].parse().unwrap_or(0);
            let seconds: u64 = parts[2]
                .split('.')
                .next()
                .and_then(|s| s.parse().ok())
                .unwrap_or(0);
            (hours * 3600 + minutes * 60 + seconds) * 1000
        }
        2 => {
            let minutes: u64 = parts[0].parse().unwrap_or(0);
            let seconds: u64 = parts[1]
                .split('.')
                .next()
                .and_then(|s| s.parse().ok())
                .unwrap_or(0);
            (minutes * 60 + seconds) * 1000
        }
        _ => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_cpu_time_hhmmss() {
        assert_eq!(parse_cpu_time("01:30:45"), (1 * 3600 + 30 * 60 + 45) * 1000);
    }

    #[test]
    fn test_parse_cpu_time_mmss() {
        assert_eq!(parse_cpu_time("05:30"), (5 * 60 + 30) * 1000);
    }

    #[test]
    fn test_parse_cpu_time_with_fractional() {
        assert_eq!(parse_cpu_time("01:30:45.123"), (1 * 3600 + 30 * 60 + 45) * 1000);
    }

    #[test]
    fn test_parse_cpu_time_zero() {
        assert_eq!(parse_cpu_time("00:00:00"), 0);
    }

    #[test]
    fn test_parse_cpu_time_invalid() {
        assert_eq!(parse_cpu_time("invalid"), 0);
    }
}
