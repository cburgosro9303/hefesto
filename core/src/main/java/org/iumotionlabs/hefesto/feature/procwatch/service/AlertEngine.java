package org.iumotionlabs.hefesto.feature.procwatch.service;

import org.iumotionlabs.hefesto.feature.procwatch.model.AlertResult;
import org.iumotionlabs.hefesto.feature.procwatch.model.AlertRule;
import org.iumotionlabs.hefesto.feature.procwatch.model.AlertRule.WindowCondition;
import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine for evaluating alert rules against process samples.
 * Supports time-windowed conditions (for, increasing, decreasing).
 */
public final class AlertEngine {

    // History of samples per process for windowed alerts
    private final Map<Long, Deque<TimestampedSample>> sampleHistory = new ConcurrentHashMap<>();

    // Track when each rule first triggered for windowed alerts
    private final Map<RuleKey, Instant> triggerStartTimes = new ConcurrentHashMap<>();

    // Max history to keep (based on largest window)
    private Duration maxHistoryDuration = Duration.ofMinutes(10);

    public AlertEngine() {}

    /**
     * Evaluates all rules against a process sample.
     *
     * @param sample the process sample to evaluate
     * @param rules  the alert rules to check
     * @return list of alert results
     */
    public List<AlertResult> evaluate(ProcessSample sample, List<AlertRule> rules) {
        // Store sample in history
        addToHistory(sample);

        List<AlertResult> results = new ArrayList<>();
        for (AlertRule rule : rules) {
            results.add(evaluateRule(sample, rule));
        }

        return results;
    }

    /**
     * Evaluates a single rule against a sample.
     */
    public AlertResult evaluateRule(ProcessSample sample, AlertRule rule) {
        double currentValue = rule.extractMetricValue(sample);

        // Simple rule (no time window)
        if (rule.window().isEmpty()) {
            boolean triggered = rule.evaluate(sample);
            return triggered
                ? AlertResult.triggered(rule, currentValue, sample)
                : AlertResult.ok(rule, currentValue, sample);
        }

        // Windowed rule
        Duration window = rule.window().get();
        WindowCondition condition = rule.windowCondition().orElse(WindowCondition.FOR);

        return switch (condition) {
            case FOR -> evaluateForCondition(sample, rule, currentValue, window);
            case INCREASING -> evaluateIncreasingCondition(sample, rule, currentValue, window);
            case DECREASING -> evaluateDecreasingCondition(sample, rule, currentValue, window);
        };
    }

    /**
     * Clears all history for a process.
     */
    public void clearHistory(long pid) {
        sampleHistory.remove(pid);
        triggerStartTimes.keySet().removeIf(k -> k.pid == pid);
    }

    /**
     * Clears all history.
     */
    public void clearAllHistory() {
        sampleHistory.clear();
        triggerStartTimes.clear();
    }

    /**
     * Sets the maximum history duration to keep.
     */
    public void setMaxHistoryDuration(Duration duration) {
        this.maxHistoryDuration = duration;
    }

    private void addToHistory(ProcessSample sample) {
        sampleHistory.computeIfAbsent(sample.pid(), k -> new LinkedList<>())
            .addLast(new TimestampedSample(sample, Instant.now()));

        // Prune old samples
        Instant cutoff = Instant.now().minus(maxHistoryDuration);
        Deque<TimestampedSample> history = sampleHistory.get(sample.pid());
        while (!history.isEmpty() && history.peekFirst().timestamp.isBefore(cutoff)) {
            history.pollFirst();
        }
    }

    private AlertResult evaluateForCondition(ProcessSample sample, AlertRule rule,
                                             double currentValue, Duration window) {
        RuleKey key = new RuleKey(sample.pid(), rule.expression());
        boolean conditionMet = rule.evaluate(sample);

        if (!conditionMet) {
            // Condition not met, reset timer
            triggerStartTimes.remove(key);
            return AlertResult.ok(rule, currentValue, sample);
        }

        // Condition is met, check if it's been met for the window duration
        Instant now = Instant.now();
        Instant triggerStart = triggerStartTimes.computeIfAbsent(key, k -> now);

        Duration elapsed = Duration.between(triggerStart, now);
        if (elapsed.compareTo(window) >= 0) {
            return AlertResult.triggered(rule, currentValue, sample);
        }

        // Still waiting for window to pass
        return AlertResult.ok(rule, currentValue, sample);
    }

    private AlertResult evaluateIncreasingCondition(ProcessSample sample, AlertRule rule,
                                                    double currentValue, Duration window) {
        Deque<TimestampedSample> history = sampleHistory.get(sample.pid());
        if (history == null || history.size() < 2) {
            return AlertResult.ok(rule, currentValue, sample);
        }

        Instant cutoff = Instant.now().minus(window);
        TimestampedSample oldest = findOldestAfter(history, cutoff);

        if (oldest == null) {
            return AlertResult.ok(rule, currentValue, sample);
        }

        double oldValue = rule.extractMetricValue(oldest.sample);
        double threshold = rule.normalizeThreshold();

        // Check if value has increased by at least threshold amount
        if (currentValue - oldValue >= threshold) {
            return AlertResult.triggered(rule, currentValue, sample);
        }

        return AlertResult.ok(rule, currentValue, sample);
    }

    private AlertResult evaluateDecreasingCondition(ProcessSample sample, AlertRule rule,
                                                    double currentValue, Duration window) {
        Deque<TimestampedSample> history = sampleHistory.get(sample.pid());
        if (history == null || history.size() < 2) {
            return AlertResult.ok(rule, currentValue, sample);
        }

        Instant cutoff = Instant.now().minus(window);
        TimestampedSample oldest = findOldestAfter(history, cutoff);

        if (oldest == null) {
            return AlertResult.ok(rule, currentValue, sample);
        }

        double oldValue = rule.extractMetricValue(oldest.sample);
        double threshold = rule.normalizeThreshold();

        // Check if value has decreased by at least threshold amount
        if (oldValue - currentValue >= threshold) {
            return AlertResult.triggered(rule, currentValue, sample);
        }

        return AlertResult.ok(rule, currentValue, sample);
    }

    private TimestampedSample findOldestAfter(Deque<TimestampedSample> history, Instant cutoff) {
        for (TimestampedSample ts : history) {
            if (!ts.timestamp.isBefore(cutoff)) {
                return ts;
            }
        }
        return history.peekFirst();
    }

    private record TimestampedSample(ProcessSample sample, Instant timestamp) {}

    private record RuleKey(long pid, String expression) {}
}
