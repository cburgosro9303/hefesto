use hefesto_domain::procwatch::alert_result::AlertResult;
use hefesto_domain::procwatch::alert_rule::AlertRule;
use hefesto_domain::procwatch::process_sample::ProcessSample;
use hefesto_platform::process_sampler::ProcessSampler;

use std::process::Command as StdCommand;
use std::sync::Arc;
use std::time::Duration;

use super::alert_engine::AlertEngine;
use super::alert_parser::AlertParser;

/// Sorting mode for "top" process listings.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TopMode {
    Cpu,
    Memory,
}

impl std::fmt::Display for TopMode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            TopMode::Cpu => f.write_str("CPU"),
            TopMode::Memory => f.write_str("MEMORY"),
        }
    }
}

/// Type of diagnostic dump to execute on alert breach.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DumpType {
    Lsof,
    Pstack,
}

/// Main service for process monitoring.
///
/// Orchestrates sampling via a platform-specific `ProcessSampler`, alert evaluation
/// through `AlertEngine`, and optional diagnostic dumps.
pub struct ProcessMonitorService {
    sampler: Arc<dyn ProcessSampler>,
    alert_engine: AlertEngine,
    alert_parser: AlertParser,
}

impl ProcessMonitorService {
    /// Creates a new service backed by the given `ProcessSampler`.
    pub fn new(sampler: Arc<dyn ProcessSampler>) -> Self {
        Self {
            sampler,
            alert_engine: AlertEngine::with_default_history(),
            alert_parser: AlertParser::new(),
        }
    }

    /// Creates a new service with a custom alert-history window.
    pub fn with_alert_history(sampler: Arc<dyn ProcessSampler>, max_history: Duration) -> Self {
        Self {
            sampler,
            alert_engine: AlertEngine::new(max_history),
            alert_parser: AlertParser::new(),
        }
    }

    // ── Sampling delegates ─────────────────────────────────────────────

    /// Returns a single sample for a process identified by PID.
    pub fn sample_by_pid(&self, pid: u32) -> Option<ProcessSample> {
        self.sampler.sample_by_pid(pid).ok().flatten()
    }

    /// Returns samples for all processes matching a name pattern (case-insensitive).
    pub fn sample_by_name(&self, name: &str) -> Vec<ProcessSample> {
        self.sampler.sample_by_name(name).unwrap_or_default()
    }

    /// Returns the top N processes sorted by CPU usage.
    pub fn top_by_cpu(&self, limit: usize) -> Vec<ProcessSample> {
        self.sampler.top_by_cpu(limit).unwrap_or_default()
    }

    /// Returns the top N processes sorted by memory usage.
    pub fn top_by_memory(&self, limit: usize) -> Vec<ProcessSample> {
        self.sampler.top_by_memory(limit).unwrap_or_default()
    }

    /// Returns all running processes.
    pub fn get_all_processes(&self) -> Vec<ProcessSample> {
        self.sampler.get_all_processes().unwrap_or_default()
    }

    // ── Alert management ───────────────────────────────────────────────

    /// Parses multiple alert expressions into rules.
    ///
    /// Expressions that fail to parse are silently skipped; collect errors at
    /// the call site if stricter validation is needed.
    pub fn parse_alerts(&self, expressions: &[String]) -> Vec<AlertRule> {
        expressions
            .iter()
            .filter_map(|expr| self.alert_parser.parse(expr).ok())
            .collect()
    }

    /// Evaluates the provided alert rules against a single process sample.
    pub fn evaluate_alerts(
        &mut self,
        sample: &ProcessSample,
        rules: &[AlertRule],
    ) -> Vec<AlertResult> {
        self.alert_engine.evaluate(sample, rules)
    }

    /// Clears all alert history (e.g., when monitoring is stopped).
    pub fn clear_alert_history(&mut self) {
        self.alert_engine.clear_all_history();
    }

    // ── Dump commands ──────────────────────────────────────────────────

    /// Executes a diagnostic dump command against the specified PID.
    ///
    /// Returns the captured stdout/stderr of the subprocess, or an error
    /// message if execution failed.
    pub fn execute_dump_command(&self, pid: u32, dump_type: DumpType) -> String {
        let pid_str = pid.to_string();
        match dump_type {
            DumpType::Pstack => execute_command(&["pstack", &pid_str]),
            DumpType::Lsof => execute_command(&["lsof", "-p", &pid_str]),
        }
    }
}

/// Runs an external command, captures its combined stdout + stderr, and returns the
/// output as a `String`. On failure, returns an error description.
fn execute_command(args: &[&str]) -> String {
    if args.is_empty() {
        return "Error: empty command".to_string();
    }

    let result = StdCommand::new(args[0])
        .args(&args[1..])
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn();

    match result {
        Ok(child) => {
            let output = match child.wait_with_output() {
                Ok(o) => o,
                Err(e) => return format!("Error waiting for command: {e}"),
            };

            let mut combined = String::new();
            if !output.stdout.is_empty() {
                combined.push_str(&String::from_utf8_lossy(&output.stdout));
            }
            if !output.stderr.is_empty() {
                if !combined.is_empty() {
                    combined.push('\n');
                }
                combined.push_str(&String::from_utf8_lossy(&output.stderr));
            }
            combined
        }
        Err(e) => format!("Error executing command: {e}"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use hefesto_domain::procwatch::alert_rule::{ComparisonOperator, MetricType, ThresholdUnit};
    use hefesto_domain::procwatch::process_sample::ProcessSample;

    /// A trivial sampler that always returns a fixed set of samples.
    struct FakeSampler {
        samples: Vec<ProcessSample>,
    }

    impl ProcessSampler for FakeSampler {
        fn get_all_processes(&self) -> anyhow::Result<Vec<ProcessSample>> {
            Ok(self.samples.clone())
        }

        fn sample_by_pid(&self, pid: u32) -> anyhow::Result<Option<ProcessSample>> {
            Ok(self.samples.iter().find(|s| s.pid == pid).cloned())
        }

        fn sample_by_name(&self, name: &str) -> anyhow::Result<Vec<ProcessSample>> {
            let lower = name.to_lowercase();
            Ok(self
                .samples
                .iter()
                .filter(|s| s.name.to_lowercase().contains(&lower))
                .cloned()
                .collect())
        }

        fn top_by_cpu(&self, limit: usize) -> anyhow::Result<Vec<ProcessSample>> {
            let mut sorted = self.samples.clone();
            sorted.sort_by(|a, b| {
                b.cpu
                    .percent_instant
                    .partial_cmp(&a.cpu.percent_instant)
                    .unwrap()
            });
            sorted.truncate(limit);
            Ok(sorted)
        }

        fn top_by_memory(&self, limit: usize) -> anyhow::Result<Vec<ProcessSample>> {
            let mut sorted = self.samples.clone();
            sorted.sort_by(|a, b| b.memory.rss_bytes.cmp(&a.memory.rss_bytes));
            sorted.truncate(limit);
            Ok(sorted)
        }
    }

    fn make_sample(pid: u32, name: &str, cpu: f64, rss: u64) -> ProcessSample {
        let mut s = ProcessSample::minimal(pid, name, "user");
        s.cpu.percent_instant = cpu;
        s.memory.rss_bytes = rss;
        s
    }

    #[test]
    fn test_sample_by_pid() {
        let sampler = Arc::new(FakeSampler {
            samples: vec![make_sample(1, "process-a", 50.0, 1024)],
        });
        let service = ProcessMonitorService::new(sampler);

        assert!(service.sample_by_pid(1).is_some());
        assert!(service.sample_by_pid(999).is_none());
    }

    #[test]
    fn test_sample_by_name() {
        let sampler = Arc::new(FakeSampler {
            samples: vec![
                make_sample(1, "nginx", 20.0, 1024),
                make_sample(2, "java", 40.0, 2048),
                make_sample(3, "nginx-worker", 10.0, 512),
            ],
        });
        let service = ProcessMonitorService::new(sampler);

        let results = service.sample_by_name("nginx");
        assert_eq!(results.len(), 2);
    }

    #[test]
    fn test_top_by_cpu() {
        let sampler = Arc::new(FakeSampler {
            samples: vec![
                make_sample(1, "a", 10.0, 0),
                make_sample(2, "b", 90.0, 0),
                make_sample(3, "c", 50.0, 0),
            ],
        });
        let service = ProcessMonitorService::new(sampler);

        let top = service.top_by_cpu(2);
        assert_eq!(top.len(), 2);
        assert_eq!(top[0].pid, 2); // highest CPU
        assert_eq!(top[1].pid, 3);
    }

    #[test]
    fn test_evaluate_alerts() {
        let sampler = Arc::new(FakeSampler {
            samples: vec![make_sample(1, "heavy", 95.0, 0)],
        });
        let mut service = ProcessMonitorService::new(sampler);

        let rule = AlertRule::simple(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
        );

        let sample = make_sample(1, "heavy", 95.0, 0);
        let results = service.evaluate_alerts(&sample, &[rule]);
        assert_eq!(results.len(), 1);
        assert!(results[0].triggered);
    }

    #[test]
    fn test_parse_alerts_skips_invalid() {
        let sampler = Arc::new(FakeSampler { samples: vec![] });
        let service = ProcessMonitorService::new(sampler);

        let expressions = vec![
            "cpu>80%".to_string(),
            "invalid!!!".to_string(),
            "rss>1GB".to_string(),
        ];
        let rules = service.parse_alerts(&expressions);
        assert_eq!(rules.len(), 2); // the invalid one is skipped
    }

    #[test]
    fn test_dump_type_display() {
        assert_eq!(format!("{}", TopMode::Cpu), "CPU");
        assert_eq!(format!("{}", TopMode::Memory), "MEMORY");
    }
}
