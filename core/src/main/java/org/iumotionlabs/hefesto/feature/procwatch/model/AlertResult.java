package org.iumotionlabs.hefesto.feature.procwatch.model;

import java.time.Instant;

/**
 * Record representing the result of an alert evaluation.
 */
public record AlertResult(
    AlertRule rule,
    boolean triggered,
    double currentValue,
    double threshold,
    ProcessSample sample,
    Instant timestamp,
    String message
) {
    /**
     * Creates a triggered alert result.
     */
    public static AlertResult triggered(AlertRule rule, double currentValue, ProcessSample sample) {
        String message = "ALERT: %s - current: %.2f, threshold: %.2f (pid=%d %s)".formatted(
            rule.describe(),
            currentValue,
            rule.normalizeThreshold(),
            sample.pid(),
            sample.name()
        );
        return new AlertResult(
            rule, true, currentValue, rule.normalizeThreshold(),
            sample, Instant.now(), message
        );
    }

    /**
     * Creates a non-triggered (OK) result.
     */
    public static AlertResult ok(AlertRule rule, double currentValue, ProcessSample sample) {
        String message = "OK: %s - current: %.2f (pid=%d %s)".formatted(
            rule.describe(),
            currentValue,
            sample.pid(),
            sample.name()
        );
        return new AlertResult(
            rule, false, currentValue, rule.normalizeThreshold(),
            sample, Instant.now(), message
        );
    }

    /**
     * Returns a formatted value based on metric type.
     */
    public String currentValueFormatted() {
        return switch (rule.metric()) {
            case CPU -> "%.1f%%".formatted(currentValue);
            case RSS, VIRTUAL, READ_BYTES, WRITE_BYTES -> formatBytes((long) currentValue);
            case THREADS, FD -> String.valueOf((int) currentValue);
        };
    }

    /**
     * Returns threshold formatted.
     */
    public String thresholdFormatted() {
        return switch (rule.metric()) {
            case CPU -> "%.1f%%".formatted(threshold);
            case RSS, VIRTUAL, READ_BYTES, WRITE_BYTES -> formatBytes((long) threshold);
            case THREADS, FD -> String.valueOf((int) threshold);
        };
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return "%.1f MB".formatted(bytes / (1024.0 * 1024));
        return "%.2f GB".formatted(bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Returns a short status line.
     */
    public String statusLine() {
        String status = triggered ? "TRIGGERED" : "OK";
        return "[%s] %s = %s (threshold: %s)".formatted(
            status,
            rule.metric().getName(),
            currentValueFormatted(),
            thresholdFormatted()
        );
    }
}
