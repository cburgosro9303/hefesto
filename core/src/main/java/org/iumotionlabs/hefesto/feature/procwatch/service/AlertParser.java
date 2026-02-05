package org.iumotionlabs.hefesto.feature.procwatch.service;

import org.iumotionlabs.hefesto.feature.procwatch.model.AlertRule;
import org.iumotionlabs.hefesto.feature.procwatch.model.AlertRule.*;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for alert rule DSL expressions.
 *
 * Supported syntax:
 * - metric>threshold       (e.g., cpu>80)
 * - metric>thresholdUNIT   (e.g., rss>1.5GB, cpu>80%)
 * - metric>threshold for Ns/m/h  (e.g., cpu>80% for 30s)
 * - metric>threshold increasing Ns/m/h
 * - metric>threshold decreasing Ns/m/h
 */
public final class AlertParser {

    // Pattern: metric operator threshold[unit] [condition duration]
    private static final Pattern RULE_PATTERN = Pattern.compile(
        "^\\s*(\\w+)\\s*(>=|<=|>|<|==|!=)\\s*([\\d.]+)\\s*(%|GB|MB|KB|B)?\\s*(?:(for|increasing|decreasing)\\s+([\\d.]+)\\s*(s|m|h))?\\s*$",
        Pattern.CASE_INSENSITIVE
    );

    public AlertParser() {}

    /**
     * Parses an alert rule expression.
     *
     * @param expression the DSL expression (e.g., "rss>1.5GB", "cpu>80% for 30s")
     * @return the parsed AlertRule
     * @throws IllegalArgumentException if the expression is invalid
     */
    public AlertRule parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Alert expression cannot be empty");
        }

        Matcher matcher = RULE_PATTERN.matcher(expression.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid alert expression: " + expression +
                ". Expected format: metric>threshold[unit] [for|increasing|decreasing duration]");
        }

        String metricStr = matcher.group(1);
        String operatorStr = matcher.group(2);
        String thresholdStr = matcher.group(3);
        String unitStr = matcher.group(4);
        String conditionStr = matcher.group(5);
        String durationValueStr = matcher.group(6);
        String durationUnitStr = matcher.group(7);

        MetricType metric = parseMetric(metricStr);
        Operator operator = Operator.fromString(operatorStr);
        double threshold = Double.parseDouble(thresholdStr);
        ThresholdUnit unit = parseUnit(unitStr, metric);

        Optional<Duration> window = Optional.empty();
        Optional<WindowCondition> windowCondition = Optional.empty();

        if (conditionStr != null && durationValueStr != null) {
            windowCondition = Optional.of(WindowCondition.fromString(conditionStr));
            window = Optional.of(parseDuration(durationValueStr, durationUnitStr));
        }

        return new AlertRule(expression, metric, operator, threshold, unit, window, windowCondition);
    }

    /**
     * Validates an expression without fully parsing it.
     *
     * @param expression the DSL expression
     * @return true if valid, false otherwise
     */
    public boolean isValid(String expression) {
        try {
            parse(expression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns a description of the supported syntax.
     */
    public static String getSyntaxHelp() {
        return """
            Alert DSL Syntax:

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
              rss>2GB increasing 5m
            """;
    }

    private MetricType parseMetric(String metric) {
        try {
            return MetricType.fromString(metric);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown metric: " + metric + ". Supported: cpu, rss, virtual, threads, fd, read, write");
        }
    }

    private ThresholdUnit parseUnit(String unitStr, MetricType metric) {
        if (unitStr == null || unitStr.isEmpty()) {
            // Default unit based on metric type
            return switch (metric) {
                case CPU -> ThresholdUnit.PERCENT;
                case RSS, VIRTUAL, READ_BYTES, WRITE_BYTES -> ThresholdUnit.BYTES;
                case THREADS, FD -> ThresholdUnit.NONE;
            };
        }
        return ThresholdUnit.fromString(unitStr);
    }

    private Duration parseDuration(String value, String unit) {
        double val = Double.parseDouble(value);
        return switch (unit.toLowerCase()) {
            case "s" -> Duration.ofSeconds((long) val);
            case "m" -> Duration.ofMinutes((long) val);
            case "h" -> Duration.ofHours((long) val);
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };
    }
}
