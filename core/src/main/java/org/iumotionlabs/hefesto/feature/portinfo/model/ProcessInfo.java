package org.iumotionlabs.hefesto.feature.portinfo.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Record representing extended process information.
 */
public record ProcessInfo(
    long pid,
    String name,
    String commandLine,
    String user,
    String workingDirectory,
    long memoryRssKb,
    long memoryVirtualKb,
    long cpuTimeMs,
    int threadCount,
    Instant startTime
) {
    /**
     * Returns the process uptime.
     */
    public Duration uptime() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, Instant.now());
    }

    /**
     * Returns memory RSS in human-readable format.
     */
    public String memoryRssFormatted() {
        return formatMemory(memoryRssKb);
    }

    /**
     * Returns virtual memory in human-readable format.
     */
    public String memoryVirtualFormatted() {
        return formatMemory(memoryVirtualKb);
    }

    /**
     * Returns CPU time in human-readable format.
     */
    public String cpuTimeFormatted() {
        long seconds = cpuTimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return "%dh%02dm%02ds".formatted(hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return "%dm%02ds".formatted(minutes, seconds % 60);
        } else {
            return "%ds".formatted(seconds);
        }
    }

    /**
     * Returns uptime in human-readable format.
     */
    public String uptimeFormatted() {
        Duration uptime = uptime();
        long days = uptime.toDays();
        long hours = uptime.toHours() % 24;
        long minutes = uptime.toMinutes() % 60;

        if (days > 0) {
            return "%dd %dh %dm".formatted(days, hours, minutes);
        } else if (hours > 0) {
            return "%dh %dm".formatted(hours, minutes);
        } else {
            return "%dm".formatted(minutes);
        }
    }

    private static String formatMemory(long kb) {
        if (kb < 1024) {
            return kb + " KB";
        } else if (kb < 1024 * 1024) {
            return "%.1f MB".formatted(kb / 1024.0);
        } else {
            return "%.2f GB".formatted(kb / (1024.0 * 1024.0));
        }
    }

    /**
     * Creates a minimal ProcessInfo with basic data.
     */
    public static ProcessInfo basic(long pid, String name, String user) {
        return new ProcessInfo(pid, name, "", user, "", 0, 0, 0, 0, null);
    }
}
