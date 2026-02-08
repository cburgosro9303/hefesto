use hefesto_domain::procwatch::alert_result::AlertResult;
use hefesto_domain::procwatch::alert_rule::{AlertRule, WindowCondition};
use hefesto_domain::procwatch::process_sample::ProcessSample;

use std::collections::{HashMap, VecDeque};
use std::time::{Duration, Instant};

/// Engine for evaluating alert rules against process samples.
///
/// Supports time-windowed conditions:
/// - `for`        -- condition must hold continuously for the specified duration
/// - `increasing` -- metric value increased by at least the threshold over the window
/// - `decreasing` -- metric value decreased by at least the threshold over the window
pub struct AlertEngine {
    /// History of timestamped samples, keyed by process PID.
    sample_history: HashMap<u32, VecDeque<TimestampedSample>>,

    /// Tracks when a windowed rule first became true for a given (PID, expression) pair.
    trigger_start_times: HashMap<RuleKey, Instant>,

    /// Maximum history to retain. Samples older than this are pruned.
    max_history_duration: Duration,
}

/// A process sample annotated with the wall-clock instant it was recorded.
struct TimestampedSample {
    sample: ProcessSample,
    timestamp: Instant,
}

/// Composite key identifying a specific rule evaluation against a specific process.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
struct RuleKey {
    pid: u32,
    expression: String,
}

impl AlertEngine {
    /// Creates a new alert engine with the given maximum history duration.
    pub fn new(max_history_duration: Duration) -> Self {
        Self {
            sample_history: HashMap::new(),
            trigger_start_times: HashMap::new(),
            max_history_duration,
        }
    }

    /// Creates a new alert engine with a default 10-minute history window.
    pub fn with_default_history() -> Self {
        Self::new(Duration::from_secs(600))
    }

    /// Evaluates all rules against a process sample.
    ///
    /// The sample is stored in the internal history before evaluation so that
    /// windowed conditions can reference past data points.
    pub fn evaluate(&mut self, sample: &ProcessSample, rules: &[AlertRule]) -> Vec<AlertResult> {
        self.add_to_history(sample);

        rules
            .iter()
            .map(|rule| self.evaluate_rule(sample, rule))
            .collect()
    }

    /// Evaluates a single rule against a sample.
    pub fn evaluate_rule(&mut self, sample: &ProcessSample, rule: &AlertRule) -> AlertResult {
        let current_value = rule.extract_metric_value(sample);

        // Simple rule (no time window)
        if rule.window.is_none() {
            let triggered = rule.evaluate(sample);
            return if triggered {
                AlertResult::triggered(rule, current_value, sample)
            } else {
                AlertResult::ok(rule, current_value, sample)
            };
        }

        // Windowed rule
        let window = rule.window.unwrap();
        let condition = rule
            .window_condition
            .as_ref()
            .cloned()
            .unwrap_or(WindowCondition::For);

        match condition {
            WindowCondition::For => {
                self.evaluate_for_condition(sample, rule, current_value, window)
            }
            WindowCondition::Increasing => {
                self.evaluate_increasing_condition(sample, rule, current_value, window)
            }
            WindowCondition::Decreasing => {
                self.evaluate_decreasing_condition(sample, rule, current_value, window)
            }
        }
    }

    /// Clears all history for a specific process.
    pub fn clear_history(&mut self, pid: u32) {
        self.sample_history.remove(&pid);
        self.trigger_start_times.retain(|k, _| k.pid != pid);
    }

    /// Clears all stored history and trigger state.
    pub fn clear_all_history(&mut self) {
        self.sample_history.clear();
        self.trigger_start_times.clear();
    }

    /// Sets the maximum history duration to retain.
    pub fn set_max_history_duration(&mut self, duration: Duration) {
        self.max_history_duration = duration;
    }

    // ── Internal helpers ───────────────────────────────────────────────

    /// Records a sample in the per-PID history and prunes entries older than
    /// `max_history_duration`.
    fn add_to_history(&mut self, sample: &ProcessSample) {
        let now = Instant::now();

        let history = self.sample_history.entry(sample.pid).or_default();

        history.push_back(TimestampedSample {
            sample: sample.clone(),
            timestamp: now,
        });

        // Prune entries older than the maximum history window.
        let cutoff = now.checked_sub(self.max_history_duration).unwrap_or(now);
        while let Some(front) = history.front() {
            if front.timestamp < cutoff {
                history.pop_front();
            } else {
                break;
            }
        }
    }

    /// Evaluates a `for` condition: the base condition must hold continuously
    /// for the specified duration before the alert triggers.
    fn evaluate_for_condition(
        &mut self,
        sample: &ProcessSample,
        rule: &AlertRule,
        current_value: f64,
        window: Duration,
    ) -> AlertResult {
        let key = RuleKey {
            pid: sample.pid,
            expression: rule.expression.clone(),
        };

        let condition_met = rule.evaluate(sample);

        if !condition_met {
            // Condition broke -- reset the timer.
            self.trigger_start_times.remove(&key);
            return AlertResult::ok(rule, current_value, sample);
        }

        // Condition holds. Record the start instant if this is the first time.
        let now = Instant::now();
        let trigger_start = *self.trigger_start_times.entry(key).or_insert(now);

        let elapsed = now.duration_since(trigger_start);
        if elapsed >= window {
            AlertResult::triggered(rule, current_value, sample)
        } else {
            // Still waiting for the window to fill.
            AlertResult::ok(rule, current_value, sample)
        }
    }

    /// Evaluates an `increasing` condition: the metric must have risen by at
    /// least the threshold amount over the specified window.
    fn evaluate_increasing_condition(
        &self,
        sample: &ProcessSample,
        rule: &AlertRule,
        current_value: f64,
        window: Duration,
    ) -> AlertResult {
        let history = match self.sample_history.get(&sample.pid) {
            Some(h) if h.len() >= 2 => h,
            _ => return AlertResult::ok(rule, current_value, sample),
        };

        let cutoff = Instant::now()
            .checked_sub(window)
            .unwrap_or_else(Instant::now);

        let oldest = match find_oldest_after(history, cutoff) {
            Some(ts) => ts,
            None => return AlertResult::ok(rule, current_value, sample),
        };

        let old_value = rule.extract_metric_value(&oldest.sample);
        let threshold = rule.normalize_threshold();

        if current_value - old_value >= threshold {
            AlertResult::triggered(rule, current_value, sample)
        } else {
            AlertResult::ok(rule, current_value, sample)
        }
    }

    /// Evaluates a `decreasing` condition: the metric must have fallen by at
    /// least the threshold amount over the specified window.
    fn evaluate_decreasing_condition(
        &self,
        sample: &ProcessSample,
        rule: &AlertRule,
        current_value: f64,
        window: Duration,
    ) -> AlertResult {
        let history = match self.sample_history.get(&sample.pid) {
            Some(h) if h.len() >= 2 => h,
            _ => return AlertResult::ok(rule, current_value, sample),
        };

        let cutoff = Instant::now()
            .checked_sub(window)
            .unwrap_or_else(Instant::now);

        let oldest = match find_oldest_after(history, cutoff) {
            Some(ts) => ts,
            None => return AlertResult::ok(rule, current_value, sample),
        };

        let old_value = rule.extract_metric_value(&oldest.sample);
        let threshold = rule.normalize_threshold();

        if old_value - current_value >= threshold {
            AlertResult::triggered(rule, current_value, sample)
        } else {
            AlertResult::ok(rule, current_value, sample)
        }
    }
}

/// Finds the oldest entry in `history` whose timestamp is at or after `cutoff`.
/// Falls back to the very first entry if no entry meets the cutoff.
fn find_oldest_after(
    history: &VecDeque<TimestampedSample>,
    cutoff: Instant,
) -> Option<&TimestampedSample> {
    for ts in history.iter() {
        if ts.timestamp >= cutoff {
            return Some(ts);
        }
    }
    history.front()
}

#[cfg(test)]
mod tests {
    use super::*;
    use hefesto_domain::procwatch::alert_rule::{ComparisonOperator, MetricType, ThresholdUnit};
    use hefesto_domain::procwatch::process_sample::ProcessSample;
    use std::thread;

    fn make_sample(pid: u32, cpu: f64, rss: u64) -> ProcessSample {
        let mut s = ProcessSample::minimal(pid, "test", "user");
        s.cpu.percent_instant = cpu;
        s.memory.rss_bytes = rss;
        s
    }

    #[test]
    fn test_simple_rule_triggered() {
        let mut engine = AlertEngine::with_default_history();
        let rule = AlertRule::simple(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
        );
        let sample = make_sample(1, 90.0, 0);

        let results = engine.evaluate(&sample, &[rule]);
        assert_eq!(results.len(), 1);
        assert!(results[0].triggered);
    }

    #[test]
    fn test_simple_rule_ok() {
        let mut engine = AlertEngine::with_default_history();
        let rule = AlertRule::simple(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
        );
        let sample = make_sample(1, 50.0, 0);

        let results = engine.evaluate(&sample, &[rule]);
        assert_eq!(results.len(), 1);
        assert!(!results[0].triggered);
    }

    #[test]
    fn test_for_condition_not_enough_time() {
        let mut engine = AlertEngine::with_default_history();
        let rule = AlertRule::windowed(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
            Duration::from_secs(10),
            WindowCondition::For,
        );
        let sample = make_sample(1, 90.0, 0);

        // First evaluation -- timer just started.
        let results = engine.evaluate(&sample, &[rule]);
        assert!(!results[0].triggered);
    }

    #[test]
    fn test_for_condition_resets_on_break() {
        let mut engine = AlertEngine::with_default_history();
        let rule = AlertRule::windowed(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
            Duration::from_millis(10),
            WindowCondition::For,
        );

        // Condition met.
        let high = make_sample(1, 90.0, 0);
        engine.evaluate(&high, std::slice::from_ref(&rule));

        // Condition breaks.
        let low = make_sample(1, 50.0, 0);
        engine.evaluate(&low, std::slice::from_ref(&rule));

        // Even after sleeping, condition should not fire because it was reset.
        thread::sleep(Duration::from_millis(20));
        let high2 = make_sample(1, 90.0, 0);
        let results = engine.evaluate(&high2, &[rule]);
        assert!(!results[0].triggered);
    }

    #[test]
    fn test_clear_history() {
        let mut engine = AlertEngine::with_default_history();
        let rule = AlertRule::simple(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
        );
        let sample = make_sample(42, 90.0, 0);
        engine.evaluate(&sample, &[rule]);

        assert!(engine.sample_history.contains_key(&42));
        engine.clear_history(42);
        assert!(!engine.sample_history.contains_key(&42));
    }

    #[test]
    fn test_clear_all_history() {
        let mut engine = AlertEngine::with_default_history();
        let rule = AlertRule::simple(
            MetricType::Cpu,
            ComparisonOperator::Greater,
            80.0,
            ThresholdUnit::Percent,
        );

        engine.evaluate(&make_sample(1, 90.0, 0), std::slice::from_ref(&rule));
        engine.evaluate(&make_sample(2, 90.0, 0), std::slice::from_ref(&rule));

        engine.clear_all_history();
        assert!(engine.sample_history.is_empty());
        assert!(engine.trigger_start_times.is_empty());
    }

    #[test]
    fn test_multiple_rules() {
        let mut engine = AlertEngine::with_default_history();
        let rules = vec![
            AlertRule::simple(
                MetricType::Cpu,
                ComparisonOperator::Greater,
                80.0,
                ThresholdUnit::Percent,
            ),
            AlertRule::simple(
                MetricType::Rss,
                ComparisonOperator::Greater,
                1_000_000.0,
                ThresholdUnit::Bytes,
            ),
        ];

        let sample = make_sample(1, 90.0, 2_000_000);
        let results = engine.evaluate(&sample, &rules);
        assert_eq!(results.len(), 2);
        assert!(results[0].triggered); // cpu > 80
        assert!(results[1].triggered); // rss > 1MB
    }
}
