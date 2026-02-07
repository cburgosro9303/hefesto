use chrono::{DateTime, Utc};
use serde::Serialize;

use super::alert_rule::{AlertRule, MetricType};
use super::process_sample::ProcessSample;

/// Result of an alert evaluation.
#[derive(Debug, Clone, Serialize)]
pub struct AlertResult {
    pub rule: AlertRule,
    pub triggered: bool,
    pub current_value: f64,
    pub threshold: f64,
    pub pid: u32,
    pub process_name: String,
    pub timestamp: DateTime<Utc>,
    pub message: String,
}

impl AlertResult {
    /// Creates a triggered alert result.
    pub fn triggered(rule: &AlertRule, current_value: f64, sample: &ProcessSample) -> Self {
        let message = format!(
            "ALERT: {} - current: {:.2}, threshold: {:.2} (pid={} {})",
            rule.describe(),
            current_value,
            rule.normalize_threshold(),
            sample.pid,
            sample.name
        );
        Self {
            rule: rule.clone(),
            triggered: true,
            current_value,
            threshold: rule.normalize_threshold(),
            pid: sample.pid,
            process_name: sample.name.clone(),
            timestamp: Utc::now(),
            message,
        }
    }

    /// Creates a non-triggered (OK) result.
    pub fn ok(rule: &AlertRule, current_value: f64, sample: &ProcessSample) -> Self {
        let message = format!(
            "OK: {} - current: {:.2} (pid={} {})",
            rule.describe(),
            current_value,
            sample.pid,
            sample.name
        );
        Self {
            rule: rule.clone(),
            triggered: false,
            current_value,
            threshold: rule.normalize_threshold(),
            pid: sample.pid,
            process_name: sample.name.clone(),
            timestamp: Utc::now(),
            message,
        }
    }

    /// Returns a formatted value based on metric type.
    pub fn current_value_formatted(&self) -> String {
        format_metric_value(&self.rule.metric, self.current_value)
    }

    /// Returns threshold formatted.
    pub fn threshold_formatted(&self) -> String {
        format_metric_value(&self.rule.metric, self.threshold)
    }

    /// Returns a short status line.
    pub fn status_line(&self) -> String {
        let status = if self.triggered { "TRIGGERED" } else { "OK" };
        format!(
            "[{}] {} = {} (threshold: {})",
            status,
            self.rule.metric.name(),
            self.current_value_formatted(),
            self.threshold_formatted()
        )
    }
}

fn format_metric_value(metric: &MetricType, value: f64) -> String {
    match metric {
        MetricType::Cpu => format!("{:.1}%", value),
        MetricType::Rss | MetricType::Virtual | MetricType::ReadBytes | MetricType::WriteBytes => {
            format_bytes(value as u64)
        }
        MetricType::Threads | MetricType::Fd => format!("{}", value as i64),
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
