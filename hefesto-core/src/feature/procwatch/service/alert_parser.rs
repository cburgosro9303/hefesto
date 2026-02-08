use hefesto_domain::procwatch::alert_rule::{
    AlertRule, ComparisonOperator, MetricType, ThresholdUnit, WindowCondition,
};
use regex::Regex;
use std::sync::LazyLock;
use std::time::Duration;

/// Regex pattern for parsing alert DSL expressions.
///
/// Captures:
///   1: metric name (word chars)
///   2: operator (>=, <=, >, <, ==, !=)
///   3: threshold value (digits and dot)
///   4: optional unit (%, GB, MB, KB, B)
///   5: optional window condition (for, increasing, decreasing)
///   6: optional duration value (digits and dot)
///   7: optional duration unit (s, m, h)
static RULE_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"(?i)^\s*([\w%]+)\s*(>=|<=|>|<|==|!=)\s*([\d.]+)\s*(%|GB|MB|KB|B)?\s*(?:(for|increasing|decreasing)\s+([\d.]+)\s*(s|m|h))?\s*$"
    ).expect("Invalid alert rule regex pattern")
});

/// Parser for alert rule DSL expressions.
///
/// Supported syntax:
/// - `metric>threshold`          (e.g., `cpu>80`)
/// - `metric>thresholdUNIT`      (e.g., `rss>1.5GB`, `cpu>80%`)
/// - `metric>threshold for Ns`   (e.g., `cpu>80% for 30s`)
/// - `metric>threshold increasing Ns`
/// - `metric>threshold decreasing Ns`
pub struct AlertParser;

impl AlertParser {
    /// Creates a new alert parser.
    pub fn new() -> Self {
        Self
    }

    /// Parses an alert rule expression.
    ///
    /// # Arguments
    /// * `expression` - The DSL expression (e.g., `"rss>1.5GB"`, `"cpu>80% for 30s"`)
    ///
    /// # Errors
    /// Returns `AlertParserError` if the expression is empty, malformed, or contains
    /// unrecognized metrics/operators/units.
    pub fn parse(&self, expression: &str) -> Result<AlertRule, AlertParserError> {
        let expression = expression.trim();
        if expression.is_empty() {
            return Err(AlertParserError::EmptyExpression);
        }

        let captures = RULE_PATTERN
            .captures(expression)
            .ok_or_else(|| AlertParserError::InvalidExpression(expression.to_string()))?;

        let metric_str = captures.get(1).unwrap().as_str();
        let operator_str = captures.get(2).unwrap().as_str();
        let threshold_str = captures.get(3).unwrap().as_str();
        let unit_str = captures.get(4).map(|m| m.as_str());
        let condition_str = captures.get(5).map(|m| m.as_str());
        let duration_value_str = captures.get(6).map(|m| m.as_str());
        let duration_unit_str = captures.get(7).map(|m| m.as_str());

        let metric = MetricType::from_str(metric_str)
            .map_err(|_| AlertParserError::UnknownMetric(metric_str.to_string()))?;

        let operator = ComparisonOperator::from_str(operator_str)
            .map_err(|_| AlertParserError::UnknownOperator(operator_str.to_string()))?;

        let threshold: f64 = threshold_str
            .parse()
            .map_err(|_| AlertParserError::InvalidThreshold(threshold_str.to_string()))?;

        let unit = parse_unit(unit_str, &metric);

        let mut window: Option<Duration> = None;
        let mut window_condition: Option<WindowCondition> = None;

        if let (Some(cond_str), Some(dur_val_str), Some(dur_unit_str)) =
            (condition_str, duration_value_str, duration_unit_str)
        {
            window_condition = Some(
                WindowCondition::from_str(cond_str)
                    .map_err(|_| AlertParserError::UnknownCondition(cond_str.to_string()))?,
            );
            window = Some(parse_duration(dur_val_str, dur_unit_str)?);
        }

        Ok(AlertRule {
            expression: expression.to_string(),
            metric,
            operator,
            threshold,
            unit,
            window,
            window_condition,
        })
    }

    /// Validates an expression without fully parsing it.
    ///
    /// Returns `true` if the expression would parse successfully, `false` otherwise.
    pub fn is_valid(&self, expression: &str) -> bool {
        self.parse(expression).is_ok()
    }

    /// Returns a description of the supported DSL syntax.
    pub fn syntax_help() -> &'static str {
        r#"Alert DSL Syntax:

Basic format:
  metric OPERATOR threshold[UNIT]

With time window:
  metric OPERATOR threshold[UNIT] CONDITION DURATION

Metrics:
  cpu, cpu%     - CPU percentage
  rss, mem      - Resident memory (RAM)
  virtual, vsz  - Virtual memory
  threads       - Thread count
  fd, fds       - File descriptors
  read          - Read bytes
  write         - Write bytes

Operators:
  >  >=  <  <=  ==  !=

Units:
  %  - Percentage (for cpu)
  B, KB, MB, GB - Bytes (for memory/io)
  (none) - Raw number (for threads, fd)

Conditions:
  for         - Condition must hold for duration
  increasing  - Value increasing over duration
  decreasing  - Value decreasing over duration

Duration:
  Ns  - N seconds (e.g., 30s)
  Nm  - N minutes (e.g., 5m)
  Nh  - N hours (e.g., 1h)

Examples:
  cpu>80%
  rss>1.5GB
  cpu>80% for 30s
  threads>100
  fd>1000
  rss>2GB increasing 5m"#
    }
}

impl Default for AlertParser {
    fn default() -> Self {
        Self::new()
    }
}

/// Errors that can occur during alert expression parsing.
#[derive(Debug, thiserror::Error)]
pub enum AlertParserError {
    #[error("Alert expression cannot be empty")]
    EmptyExpression,

    #[error(
        "Invalid alert expression: {0}. \
         Expected format: metric>threshold[unit] [for|increasing|decreasing duration]"
    )]
    InvalidExpression(String),

    #[error("Unknown metric: {0}. Supported: cpu, rss, virtual, threads, fd, read, write")]
    UnknownMetric(String),

    #[error("Unknown operator: {0}")]
    UnknownOperator(String),

    #[error("Invalid threshold value: {0}")]
    InvalidThreshold(String),

    #[error("Unknown window condition: {0}")]
    UnknownCondition(String),

    #[error("Invalid duration value: {0}")]
    InvalidDuration(String),

    #[error("Unknown duration unit: {0}")]
    UnknownDurationUnit(String),
}

/// Resolves the threshold unit from the optional unit string, falling back to a
/// default based on the metric type when no unit is provided.
fn parse_unit(unit_str: Option<&str>, metric: &MetricType) -> ThresholdUnit {
    match unit_str {
        Some(u) if !u.is_empty() => ThresholdUnit::from_str(u),
        _ => match metric {
            MetricType::Cpu => ThresholdUnit::Percent,
            MetricType::Rss
            | MetricType::Virtual
            | MetricType::ReadBytes
            | MetricType::WriteBytes => ThresholdUnit::Bytes,
            MetricType::Threads | MetricType::Fd => ThresholdUnit::None,
        },
    }
}

/// Parses a duration from a numeric string and a unit character.
fn parse_duration(value_str: &str, unit: &str) -> Result<Duration, AlertParserError> {
    let val: f64 = value_str
        .parse()
        .map_err(|_| AlertParserError::InvalidDuration(value_str.to_string()))?;

    match unit.to_lowercase().as_str() {
        "s" => Ok(Duration::from_secs(val as u64)),
        "m" => Ok(Duration::from_secs((val * 60.0) as u64)),
        "h" => Ok(Duration::from_secs((val * 3600.0) as u64)),
        other => Err(AlertParserError::UnknownDurationUnit(other.to_string())),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parser() -> AlertParser {
        AlertParser::new()
    }

    // ── Basic parsing ──────────────────────────────────────────────────

    #[test]
    fn test_parse_cpu_percent() {
        let rule = parser().parse("cpu>80%").unwrap();
        assert_eq!(rule.metric, MetricType::Cpu);
        assert_eq!(rule.operator, ComparisonOperator::Greater);
        assert!((rule.threshold - 80.0).abs() < f64::EPSILON);
        assert_eq!(rule.unit, ThresholdUnit::Percent);
        assert!(rule.window.is_none());
        assert!(rule.window_condition.is_none());
    }

    #[test]
    fn test_parse_rss_gb() {
        let rule = parser().parse("rss>1.5GB").unwrap();
        assert_eq!(rule.metric, MetricType::Rss);
        assert_eq!(rule.operator, ComparisonOperator::Greater);
        assert!((rule.threshold - 1.5).abs() < f64::EPSILON);
        assert_eq!(rule.unit, ThresholdUnit::Gb);
        assert!(rule.window.is_none());
    }

    #[test]
    fn test_parse_rss_mb() {
        let rule = parser().parse("mem>=512MB").unwrap();
        assert_eq!(rule.metric, MetricType::Rss);
        assert_eq!(rule.operator, ComparisonOperator::GreaterEq);
        assert!((rule.threshold - 512.0).abs() < f64::EPSILON);
        assert_eq!(rule.unit, ThresholdUnit::Mb);
    }

    #[test]
    fn test_parse_threads_no_unit() {
        let rule = parser().parse("threads>100").unwrap();
        assert_eq!(rule.metric, MetricType::Threads);
        assert_eq!(rule.operator, ComparisonOperator::Greater);
        assert!((rule.threshold - 100.0).abs() < f64::EPSILON);
        assert_eq!(rule.unit, ThresholdUnit::None);
    }

    #[test]
    fn test_parse_fd_count() {
        let rule = parser().parse("fd>1000").unwrap();
        assert_eq!(rule.metric, MetricType::Fd);
        assert_eq!(rule.operator, ComparisonOperator::Greater);
        assert!((rule.threshold - 1000.0).abs() < f64::EPSILON);
    }

    #[test]
    fn test_parse_virtual_kb() {
        let rule = parser().parse("virtual<=2048KB").unwrap();
        assert_eq!(rule.metric, MetricType::Virtual);
        assert_eq!(rule.operator, ComparisonOperator::LessEq);
        assert_eq!(rule.unit, ThresholdUnit::Kb);
    }

    // ── Operators ──────────────────────────────────────────────────────

    #[test]
    fn test_parse_all_operators() {
        let cases = [
            ("cpu>50%", ComparisonOperator::Greater),
            ("cpu>=50%", ComparisonOperator::GreaterEq),
            ("cpu<50%", ComparisonOperator::Less),
            ("cpu<=50%", ComparisonOperator::LessEq),
            ("cpu==50%", ComparisonOperator::Equals),
            ("cpu!=50%", ComparisonOperator::NotEquals),
        ];
        for (expr, expected_op) in &cases {
            let rule = parser().parse(expr).unwrap();
            assert_eq!(rule.operator, *expected_op, "Failed for expression: {expr}");
        }
    }

    // ── Windowed rules ─────────────────────────────────────────────────

    #[test]
    fn test_parse_for_window() {
        let rule = parser().parse("cpu>80% for 30s").unwrap();
        assert_eq!(rule.metric, MetricType::Cpu);
        assert_eq!(rule.operator, ComparisonOperator::Greater);
        assert!((rule.threshold - 80.0).abs() < f64::EPSILON);
        assert_eq!(rule.window, Some(Duration::from_secs(30)));
        assert_eq!(rule.window_condition, Some(WindowCondition::For));
    }

    #[test]
    fn test_parse_increasing_window() {
        let rule = parser().parse("rss>2GB increasing 5m").unwrap();
        assert_eq!(rule.metric, MetricType::Rss);
        assert_eq!(rule.window, Some(Duration::from_secs(300)));
        assert_eq!(rule.window_condition, Some(WindowCondition::Increasing));
    }

    #[test]
    fn test_parse_decreasing_window() {
        let rule = parser().parse("threads>10 decreasing 1h").unwrap();
        assert_eq!(rule.metric, MetricType::Threads);
        assert_eq!(rule.window, Some(Duration::from_secs(3600)));
        assert_eq!(rule.window_condition, Some(WindowCondition::Decreasing));
    }

    // ── Metric aliases ─────────────────────────────────────────────────

    #[test]
    fn test_metric_aliases() {
        assert_eq!(parser().parse("cpu%>50").unwrap().metric, MetricType::Cpu);
        assert_eq!(parser().parse("mem>100MB").unwrap().metric, MetricType::Rss);
        assert_eq!(
            parser().parse("memory>100MB").unwrap().metric,
            MetricType::Rss
        );
        assert_eq!(
            parser().parse("vsz>100MB").unwrap().metric,
            MetricType::Virtual
        );
        assert_eq!(
            parser().parse("virt>100MB").unwrap().metric,
            MetricType::Virtual
        );
        assert_eq!(parser().parse("fds>100").unwrap().metric, MetricType::Fd);
        assert_eq!(parser().parse("files>100").unwrap().metric, MetricType::Fd);
        assert_eq!(
            parser().parse("read>100MB").unwrap().metric,
            MetricType::ReadBytes
        );
        assert_eq!(
            parser().parse("write>100MB").unwrap().metric,
            MetricType::WriteBytes
        );
    }

    // ── Default units ──────────────────────────────────────────────────

    #[test]
    fn test_default_unit_for_cpu() {
        let rule = parser().parse("cpu>80").unwrap();
        assert_eq!(rule.unit, ThresholdUnit::Percent);
    }

    #[test]
    fn test_default_unit_for_memory() {
        let rule = parser().parse("rss>1024").unwrap();
        assert_eq!(rule.unit, ThresholdUnit::Bytes);
    }

    #[test]
    fn test_default_unit_for_threads() {
        let rule = parser().parse("threads>50").unwrap();
        assert_eq!(rule.unit, ThresholdUnit::None);
    }

    // ── Whitespace handling ────────────────────────────────────────────

    #[test]
    fn test_whitespace_tolerance() {
        let rule = parser().parse("  cpu  >  80  %  for  30  s  ").unwrap();
        assert_eq!(rule.metric, MetricType::Cpu);
        assert!((rule.threshold - 80.0).abs() < f64::EPSILON);
        assert_eq!(rule.window, Some(Duration::from_secs(30)));
    }

    // ── Case insensitivity ─────────────────────────────────────────────

    #[test]
    fn test_case_insensitive() {
        let rule = parser().parse("CPU>80% FOR 30s").unwrap();
        assert_eq!(rule.metric, MetricType::Cpu);
        assert_eq!(rule.window_condition, Some(WindowCondition::For));
    }

    // ── Error cases ────────────────────────────────────────────────────

    #[test]
    fn test_empty_expression() {
        let result = parser().parse("");
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            AlertParserError::EmptyExpression
        ));
    }

    #[test]
    fn test_whitespace_only_expression() {
        let result = parser().parse("   ");
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            AlertParserError::EmptyExpression
        ));
    }

    #[test]
    fn test_invalid_expression_format() {
        let result = parser().parse("not a valid expression");
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            AlertParserError::InvalidExpression(_)
        ));
    }

    #[test]
    fn test_unknown_metric() {
        let result = parser().parse("bogus>80%");
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            AlertParserError::UnknownMetric(_)
        ));
    }

    // ── Validation helper ──────────────────────────────────────────────

    #[test]
    fn test_is_valid() {
        assert!(parser().is_valid("cpu>80%"));
        assert!(parser().is_valid("rss>1.5GB for 30s"));
        assert!(!parser().is_valid(""));
        assert!(!parser().is_valid("garbage"));
    }

    // ── Normalized threshold ───────────────────────────────────────────

    #[test]
    fn test_normalize_threshold_cpu() {
        let rule = parser().parse("cpu>80%").unwrap();
        assert!((rule.normalize_threshold() - 80.0).abs() < f64::EPSILON);
    }

    #[test]
    fn test_normalize_threshold_gb() {
        let rule = parser().parse("rss>1.5GB").unwrap();
        let expected = 1.5 * 1024.0 * 1024.0 * 1024.0;
        assert!((rule.normalize_threshold() - expected).abs() < 1.0);
    }

    #[test]
    fn test_normalize_threshold_threads() {
        let rule = parser().parse("threads>100").unwrap();
        assert!((rule.normalize_threshold() - 100.0).abs() < f64::EPSILON);
    }

    // ── Expression string preserved ────────────────────────────────────

    #[test]
    fn test_expression_preserved() {
        let rule = parser().parse("cpu>80% for 30s").unwrap();
        assert_eq!(rule.expression, "cpu>80% for 30s");
    }

    // ── Read/write bytes ───────────────────────────────────────────────

    #[test]
    fn test_parse_read_bytes() {
        let rule = parser().parse("read>100MB").unwrap();
        assert_eq!(rule.metric, MetricType::ReadBytes);
        assert_eq!(rule.unit, ThresholdUnit::Mb);
    }

    #[test]
    fn test_parse_write_bytes() {
        let rule = parser().parse("write>500KB").unwrap();
        assert_eq!(rule.metric, MetricType::WriteBytes);
        assert_eq!(rule.unit, ThresholdUnit::Kb);
    }
}
