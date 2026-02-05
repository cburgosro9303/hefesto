package org.iumotionlabs.hefesto.feature.procwatch.model;

import java.time.Duration;
import java.util.Optional;

/**
 * Record representing an alert rule with condition and optional time window.
 */
public record AlertRule(
    String expression,
    MetricType metric,
    Operator operator,
    double threshold,
    ThresholdUnit unit,
    Optional<Duration> window,
    Optional<WindowCondition> windowCondition
) {
    /**
     * Metrics that can be monitored.
     */
    public enum MetricType {
        CPU("cpu", "CPU percentage"),
        RSS("rss", "Resident Set Size (memory)"),
        VIRTUAL("virtual", "Virtual memory"),
        THREADS("threads", "Thread count"),
        FD("fd", "File descriptors"),
        READ_BYTES("read", "Read bytes"),
        WRITE_BYTES("write", "Write bytes");

        private final String name;
        private final String description;

        MetricType(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public static MetricType fromString(String s) {
            String lower = s.toLowerCase();
            return switch (lower) {
                case "cpu", "cpu%" -> CPU;
                case "rss", "mem", "memory" -> RSS;
                case "virtual", "vsz", "virt" -> VIRTUAL;
                case "threads", "thread" -> THREADS;
                case "fd", "fds", "files" -> FD;
                case "read", "read_bytes" -> READ_BYTES;
                case "write", "write_bytes" -> WRITE_BYTES;
                default -> throw new IllegalArgumentException("Unknown metric: " + s);
            };
        }
    }

    /**
     * Comparison operators.
     */
    public enum Operator {
        GREATER(">"),
        GREATER_EQ(">="),
        LESS("<"),
        LESS_EQ("<="),
        EQUALS("=="),
        NOT_EQUALS("!=");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public boolean evaluate(double value, double threshold) {
            return switch (this) {
                case GREATER -> value > threshold;
                case GREATER_EQ -> value >= threshold;
                case LESS -> value < threshold;
                case LESS_EQ -> value <= threshold;
                case EQUALS -> Math.abs(value - threshold) < 0.001;
                case NOT_EQUALS -> Math.abs(value - threshold) >= 0.001;
            };
        }

        public static Operator fromString(String s) {
            return switch (s) {
                case ">" -> GREATER;
                case ">=" -> GREATER_EQ;
                case "<" -> LESS;
                case "<=" -> LESS_EQ;
                case "==", "=" -> EQUALS;
                case "!=", "<>" -> NOT_EQUALS;
                default -> throw new IllegalArgumentException("Unknown operator: " + s);
            };
        }
    }

    /**
     * Unit for threshold values.
     */
    public enum ThresholdUnit {
        NONE(1),
        PERCENT(1),
        BYTES(1),
        KB(1024),
        MB(1024 * 1024),
        GB(1024L * 1024 * 1024);

        private final long multiplier;

        ThresholdUnit(long multiplier) {
            this.multiplier = multiplier;
        }

        public long multiplier() {
            return multiplier;
        }

        public double toBytes(double value) {
            return value * multiplier;
        }

        public static ThresholdUnit fromString(String s) {
            if (s == null || s.isEmpty()) return NONE;
            return switch (s.toUpperCase()) {
                case "%" -> PERCENT;
                case "B", "BYTES" -> BYTES;
                case "K", "KB" -> KB;
                case "M", "MB" -> MB;
                case "G", "GB" -> GB;
                default -> NONE;
            };
        }
    }

    /**
     * Window conditions for time-based alerts.
     */
    public enum WindowCondition {
        FOR("for"),           // Condition must hold for duration
        INCREASING("increasing"), // Value increasing over duration
        DECREASING("decreasing"); // Value decreasing over duration

        private final String keyword;

        WindowCondition(String keyword) {
            this.keyword = keyword;
        }

        public String keyword() {
            return keyword;
        }

        public static WindowCondition fromString(String s) {
            return switch (s.toLowerCase()) {
                case "for" -> FOR;
                case "increasing" -> INCREASING;
                case "decreasing" -> DECREASING;
                default -> throw new IllegalArgumentException("Unknown window condition: " + s);
            };
        }
    }

    /**
     * Evaluates this rule against a process sample.
     */
    public boolean evaluate(ProcessSample sample) {
        double value = extractMetricValue(sample);
        double normalizedThreshold = normalizeThreshold();
        return operator.evaluate(value, normalizedThreshold);
    }

    /**
     * Extracts the metric value from a sample.
     */
    public double extractMetricValue(ProcessSample sample) {
        return switch (metric) {
            case CPU -> sample.cpu().percentInstant();
            case RSS -> sample.memory().rssBytes();
            case VIRTUAL -> sample.memory().virtualBytes();
            case THREADS -> sample.threadCount();
            case FD -> sample.openFileDescriptors();
            case READ_BYTES -> sample.io().readBytes();
            case WRITE_BYTES -> sample.io().writeBytes();
        };
    }

    /**
     * Normalizes threshold based on unit.
     */
    public double normalizeThreshold() {
        if (metric == MetricType.CPU || metric == MetricType.THREADS || metric == MetricType.FD) {
            return threshold;
        }
        return unit.toBytes(threshold);
    }

    /**
     * Returns a human-readable description.
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(metric.getName());
        sb.append(" ").append(operator.symbol());
        sb.append(" ").append(formatThreshold());

        window.ifPresent(w -> {
            sb.append(" ");
            windowCondition.ifPresent(c -> sb.append(c.keyword()).append(" "));
            sb.append(formatDuration(w));
        });

        return sb.toString();
    }

    private String formatThreshold() {
        if (unit == ThresholdUnit.PERCENT) {
            return "%.0f%%".formatted(threshold);
        } else if (unit == ThresholdUnit.GB) {
            return "%.1fGB".formatted(threshold);
        } else if (unit == ThresholdUnit.MB) {
            return "%.0fMB".formatted(threshold);
        } else if (unit == ThresholdUnit.KB) {
            return "%.0fKB".formatted(threshold);
        } else if (threshold == (long) threshold) {
            return String.valueOf((long) threshold);
        } else {
            return String.valueOf(threshold);
        }
    }

    private String formatDuration(Duration d) {
        long seconds = d.getSeconds();
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }

    /**
     * Creates a simple threshold rule.
     */
    public static AlertRule simple(MetricType metric, Operator op, double threshold, ThresholdUnit unit) {
        String expr = metric.getName() + op.symbol() + threshold + (unit != ThresholdUnit.NONE ? unit.name() : "");
        return new AlertRule(expr, metric, op, threshold, unit, Optional.empty(), Optional.empty());
    }

    /**
     * Creates a windowed rule.
     */
    public static AlertRule windowed(MetricType metric, Operator op, double threshold, ThresholdUnit unit,
                                     Duration window, WindowCondition condition) {
        String expr = metric.getName() + op.symbol() + threshold +
                     (unit != ThresholdUnit.NONE ? unit.name() : "") +
                     " " + condition.keyword() + " " + window.getSeconds() + "s";
        return new AlertRule(expr, metric, op, threshold, unit, Optional.of(window), Optional.of(condition));
    }
}
