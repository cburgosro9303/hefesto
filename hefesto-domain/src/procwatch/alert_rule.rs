use serde::Serialize;
use std::time::Duration;
use thiserror::Error;

use super::process_sample::ProcessSample;

#[derive(Debug, Error)]
pub enum AlertRuleError {
    #[error("Unknown metric: {0}")]
    UnknownMetric(String),
    #[error("Unknown operator: {0}")]
    UnknownOperator(String),
    #[error("Unknown window condition: {0}")]
    UnknownWindowCondition(String),
}

/// Metrics that can be monitored.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum MetricType {
    Cpu,
    Rss,
    Virtual,
    Threads,
    Fd,
    ReadBytes,
    WriteBytes,
}

impl MetricType {
    pub fn name(&self) -> &str {
        match self {
            MetricType::Cpu => "cpu",
            MetricType::Rss => "rss",
            MetricType::Virtual => "virtual",
            MetricType::Threads => "threads",
            MetricType::Fd => "fd",
            MetricType::ReadBytes => "read",
            MetricType::WriteBytes => "write",
        }
    }

    pub fn description(&self) -> &str {
        match self {
            MetricType::Cpu => "CPU percentage",
            MetricType::Rss => "Resident Set Size (memory)",
            MetricType::Virtual => "Virtual memory",
            MetricType::Threads => "Thread count",
            MetricType::Fd => "File descriptors",
            MetricType::ReadBytes => "Read bytes",
            MetricType::WriteBytes => "Write bytes",
        }
    }

    pub fn parse(s: &str) -> Result<Self, AlertRuleError> {
        match s.to_lowercase().as_str() {
            "cpu" | "cpu%" => Ok(MetricType::Cpu),
            "rss" | "mem" | "memory" => Ok(MetricType::Rss),
            "virtual" | "vsz" | "virt" => Ok(MetricType::Virtual),
            "threads" | "thread" => Ok(MetricType::Threads),
            "fd" | "fds" | "files" => Ok(MetricType::Fd),
            "read" | "read_bytes" => Ok(MetricType::ReadBytes),
            "write" | "write_bytes" => Ok(MetricType::WriteBytes),
            other => Err(AlertRuleError::UnknownMetric(other.to_string())),
        }
    }
}

/// Comparison operators.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum ComparisonOperator {
    Greater,
    GreaterEq,
    Less,
    LessEq,
    Equals,
    NotEquals,
}

impl ComparisonOperator {
    pub fn symbol(&self) -> &str {
        match self {
            ComparisonOperator::Greater => ">",
            ComparisonOperator::GreaterEq => ">=",
            ComparisonOperator::Less => "<",
            ComparisonOperator::LessEq => "<=",
            ComparisonOperator::Equals => "==",
            ComparisonOperator::NotEquals => "!=",
        }
    }

    pub fn evaluate(&self, value: f64, threshold: f64) -> bool {
        match self {
            ComparisonOperator::Greater => value > threshold,
            ComparisonOperator::GreaterEq => value >= threshold,
            ComparisonOperator::Less => value < threshold,
            ComparisonOperator::LessEq => value <= threshold,
            ComparisonOperator::Equals => (value - threshold).abs() < 0.001,
            ComparisonOperator::NotEquals => (value - threshold).abs() >= 0.001,
        }
    }

    pub fn parse(s: &str) -> Result<Self, AlertRuleError> {
        match s {
            ">" => Ok(ComparisonOperator::Greater),
            ">=" => Ok(ComparisonOperator::GreaterEq),
            "<" => Ok(ComparisonOperator::Less),
            "<=" => Ok(ComparisonOperator::LessEq),
            "==" | "=" => Ok(ComparisonOperator::Equals),
            "!=" | "<>" => Ok(ComparisonOperator::NotEquals),
            other => Err(AlertRuleError::UnknownOperator(other.to_string())),
        }
    }
}

impl std::fmt::Display for ComparisonOperator {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.symbol())
    }
}

/// Unit for threshold values.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum ThresholdUnit {
    None,
    Percent,
    Bytes,
    Kb,
    Mb,
    Gb,
}

impl ThresholdUnit {
    pub fn multiplier(&self) -> u64 {
        match self {
            ThresholdUnit::None | ThresholdUnit::Percent | ThresholdUnit::Bytes => 1,
            ThresholdUnit::Kb => 1024,
            ThresholdUnit::Mb => 1024 * 1024,
            ThresholdUnit::Gb => 1024 * 1024 * 1024,
        }
    }

    pub fn to_bytes(&self, value: f64) -> f64 {
        value * self.multiplier() as f64
    }

    pub fn parse(s: &str) -> Self {
        if s.is_empty() {
            return ThresholdUnit::None;
        }
        match s.to_uppercase().as_str() {
            "%" => ThresholdUnit::Percent,
            "B" | "BYTES" => ThresholdUnit::Bytes,
            "K" | "KB" => ThresholdUnit::Kb,
            "M" | "MB" => ThresholdUnit::Mb,
            "G" | "GB" => ThresholdUnit::Gb,
            _ => ThresholdUnit::None,
        }
    }
}

/// Window conditions for time-based alerts.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum WindowCondition {
    For,
    Increasing,
    Decreasing,
}

impl WindowCondition {
    pub fn keyword(&self) -> &str {
        match self {
            WindowCondition::For => "for",
            WindowCondition::Increasing => "increasing",
            WindowCondition::Decreasing => "decreasing",
        }
    }

    pub fn parse(s: &str) -> Result<Self, AlertRuleError> {
        match s.to_lowercase().as_str() {
            "for" => Ok(WindowCondition::For),
            "increasing" => Ok(WindowCondition::Increasing),
            "decreasing" => Ok(WindowCondition::Decreasing),
            other => Err(AlertRuleError::UnknownWindowCondition(other.to_string())),
        }
    }
}

/// An alert rule with condition and optional time window.
#[derive(Debug, Clone, Serialize)]
pub struct AlertRule {
    pub expression: String,
    pub metric: MetricType,
    pub operator: ComparisonOperator,
    pub threshold: f64,
    pub unit: ThresholdUnit,
    pub window: Option<Duration>,
    pub window_condition: Option<WindowCondition>,
}

impl AlertRule {
    /// Evaluates this rule against a process sample.
    pub fn evaluate(&self, sample: &ProcessSample) -> bool {
        let value = self.extract_metric_value(sample);
        let normalized_threshold = self.normalize_threshold();
        self.operator.evaluate(value, normalized_threshold)
    }

    /// Extracts the metric value from a sample.
    pub fn extract_metric_value(&self, sample: &ProcessSample) -> f64 {
        match self.metric {
            MetricType::Cpu => sample.cpu.percent_instant,
            MetricType::Rss => sample.memory.rss_bytes as f64,
            MetricType::Virtual => sample.memory.virtual_bytes as f64,
            MetricType::Threads => sample.thread_count as f64,
            MetricType::Fd => sample.open_file_descriptors as f64,
            MetricType::ReadBytes => sample.io.read_bytes as f64,
            MetricType::WriteBytes => sample.io.write_bytes as f64,
        }
    }

    /// Normalizes threshold based on unit.
    pub fn normalize_threshold(&self) -> f64 {
        match self.metric {
            MetricType::Cpu | MetricType::Threads | MetricType::Fd => self.threshold,
            _ => self.unit.to_bytes(self.threshold),
        }
    }

    /// Returns a human-readable description.
    pub fn describe(&self) -> String {
        let mut sb = String::new();
        sb.push_str(self.metric.name());
        sb.push(' ');
        sb.push_str(self.operator.symbol());
        sb.push(' ');
        sb.push_str(&self.format_threshold());

        if let Some(ref w) = self.window {
            sb.push(' ');
            if let Some(ref wc) = self.window_condition {
                sb.push_str(wc.keyword());
                sb.push(' ');
            }
            sb.push_str(&format_duration(w));
        }

        sb
    }

    fn format_threshold(&self) -> String {
        match self.unit {
            ThresholdUnit::Percent => format!("{:.0}%", self.threshold),
            ThresholdUnit::Gb => format!("{:.1}GB", self.threshold),
            ThresholdUnit::Mb => format!("{:.0}MB", self.threshold),
            ThresholdUnit::Kb => format!("{:.0}KB", self.threshold),
            _ => {
                if self.threshold == (self.threshold as i64) as f64 {
                    format!("{}", self.threshold as i64)
                } else {
                    format!("{}", self.threshold)
                }
            }
        }
    }

    /// Creates a simple threshold rule.
    pub fn simple(
        metric: MetricType,
        operator: ComparisonOperator,
        threshold: f64,
        unit: ThresholdUnit,
    ) -> Self {
        let unit_str = match unit {
            ThresholdUnit::None => String::new(),
            ThresholdUnit::Percent => "%".to_string(),
            ThresholdUnit::Bytes => "B".to_string(),
            ThresholdUnit::Kb => "KB".to_string(),
            ThresholdUnit::Mb => "MB".to_string(),
            ThresholdUnit::Gb => "GB".to_string(),
        };
        let expr = format!(
            "{}{}{}{}",
            metric.name(),
            operator.symbol(),
            threshold,
            unit_str
        );
        Self {
            expression: expr,
            metric,
            operator,
            threshold,
            unit,
            window: None,
            window_condition: None,
        }
    }

    /// Creates a windowed rule.
    pub fn windowed(
        metric: MetricType,
        operator: ComparisonOperator,
        threshold: f64,
        unit: ThresholdUnit,
        window: Duration,
        condition: WindowCondition,
    ) -> Self {
        let unit_str = match unit {
            ThresholdUnit::None => String::new(),
            ThresholdUnit::Percent => "%".to_string(),
            ThresholdUnit::Bytes => "B".to_string(),
            ThresholdUnit::Kb => "KB".to_string(),
            ThresholdUnit::Mb => "MB".to_string(),
            ThresholdUnit::Gb => "GB".to_string(),
        };
        let expr = format!(
            "{}{}{}{} {} {}s",
            metric.name(),
            operator.symbol(),
            threshold,
            unit_str,
            condition.keyword(),
            window.as_secs()
        );
        Self {
            expression: expr,
            metric,
            operator,
            threshold,
            unit,
            window: Some(window),
            window_condition: Some(condition),
        }
    }
}

fn format_duration(d: &Duration) -> String {
    let seconds = d.as_secs();
    if seconds < 60 {
        format!("{}s", seconds)
    } else if seconds < 3600 {
        format!("{}m", seconds / 60)
    } else {
        format!("{}h", seconds / 3600)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_metric_type_from_str() {
        assert_eq!(MetricType::parse("cpu").unwrap(), MetricType::Cpu);
        assert_eq!(MetricType::parse("CPU%").unwrap(), MetricType::Cpu);
        assert_eq!(MetricType::parse("rss").unwrap(), MetricType::Rss);
        assert_eq!(MetricType::parse("mem").unwrap(), MetricType::Rss);
        assert_eq!(MetricType::parse("memory").unwrap(), MetricType::Rss);
        assert_eq!(MetricType::parse("virtual").unwrap(), MetricType::Virtual);
        assert_eq!(MetricType::parse("threads").unwrap(), MetricType::Threads);
        assert!(MetricType::parse("invalid").is_err());
    }

    #[test]
    fn test_operator_evaluate() {
        assert!(ComparisonOperator::Greater.evaluate(90.0, 80.0));
        assert!(!ComparisonOperator::Greater.evaluate(80.0, 90.0));
        assert!(ComparisonOperator::GreaterEq.evaluate(80.0, 80.0));
        assert!(ComparisonOperator::Less.evaluate(70.0, 80.0));
        assert!(ComparisonOperator::Equals.evaluate(80.0, 80.0));
        assert!(ComparisonOperator::NotEquals.evaluate(70.0, 80.0));
    }

    #[test]
    fn test_simple_rule_evaluate() {
        let rule = AlertRule::simple(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
        );

        let mut sample = ProcessSample::minimal(1, "test", "user");
        sample.cpu.percent_instant = 90.0;
        assert!(rule.evaluate(&sample));

        sample.cpu.percent_instant = 70.0;
        assert!(!rule.evaluate(&sample));
    }

    #[test]
    fn test_describe() {
        let rule = AlertRule::simple(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
        );
        assert_eq!(rule.describe(), "cpu > 80%");
    }

    #[test]
    fn test_describe_windowed() {
        let rule = AlertRule::windowed(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
            Duration::from_secs(30),
            WindowCondition::For,
        );
        assert_eq!(rule.describe(), "cpu > 80% for 30s");
    }

    #[test]
    fn test_threshold_unit_to_bytes() {
        assert_eq!(
            ThresholdUnit::Gb.to_bytes(1.5),
            1.5 * 1024.0 * 1024.0 * 1024.0
        );
        assert_eq!(ThresholdUnit::Mb.to_bytes(100.0), 100.0 * 1024.0 * 1024.0);
        assert_eq!(ThresholdUnit::Kb.to_bytes(512.0), 512.0 * 1024.0);
    }
}
