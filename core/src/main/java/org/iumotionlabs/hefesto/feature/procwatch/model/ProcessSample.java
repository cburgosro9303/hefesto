package org.iumotionlabs.hefesto.feature.procwatch.model;

import java.time.Instant;

/**
 * Record representing a single sample of process metrics at a point in time.
 */
public record ProcessSample(
    long pid,
    String name,
    String commandLine,
    String user,
    ProcessState state,
    CpuMetrics cpu,
    MemoryMetrics memory,
    IoMetrics io,
    int threadCount,
    int openFileDescriptors,
    Instant startTime,
    Instant sampleTime
) {
    /**
     * Process state enumeration.
     */
    public enum ProcessState {
        RUNNING("R", "Running"),
        SLEEPING("S", "Sleeping"),
        WAITING("D", "Waiting"),
        ZOMBIE("Z", "Zombie"),
        STOPPED("T", "Stopped"),
        IDLE("I", "Idle"),
        UNKNOWN("?", "Unknown");

        private final String code;
        private final String description;

        ProcessState(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String code() {
            return code;
        }

        public String description() {
            return description;
        }

        public static ProcessState fromCode(String code) {
            if (code == null || code.isEmpty()) return UNKNOWN;
            char c = code.charAt(0);
            return switch (c) {
                case 'R' -> RUNNING;
                case 'S', 's' -> SLEEPING;
                case 'D' -> WAITING;
                case 'Z' -> ZOMBIE;
                case 'T', 't' -> STOPPED;
                case 'I' -> IDLE;
                default -> UNKNOWN;
            };
        }
    }

    /**
     * CPU metrics.
     */
    public record CpuMetrics(
        double percentInstant,
        double percentAverage,
        long userTimeMs,
        long systemTimeMs,
        long totalTimeMs
    ) {
        public static CpuMetrics zero() {
            return new CpuMetrics(0, 0, 0, 0, 0);
        }

        public String percentFormatted() {
            return "%.1f%%".formatted(percentInstant);
        }

        public String averageFormatted() {
            return "%.1f%%".formatted(percentAverage);
        }
    }

    /**
     * Memory metrics.
     */
    public record MemoryMetrics(
        long rssBytes,
        long virtualBytes,
        long sharedBytes,
        double percentOfTotal
    ) {
        public static MemoryMetrics zero() {
            return new MemoryMetrics(0, 0, 0, 0);
        }

        public String rssFormatted() {
            return formatBytes(rssBytes);
        }

        public String virtualFormatted() {
            return formatBytes(virtualBytes);
        }

        public long rssMb() {
            return rssBytes / (1024 * 1024);
        }

        public long rssGb() {
            return rssBytes / (1024 * 1024 * 1024);
        }

        private static String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return "%.1f MB".formatted(bytes / (1024.0 * 1024));
            return "%.2f GB".formatted(bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * I/O metrics.
     */
    public record IoMetrics(
        long readBytes,
        long writeBytes,
        long readOps,
        long writeOps
    ) {
        public static IoMetrics zero() {
            return new IoMetrics(0, 0, 0, 0);
        }

        public String readFormatted() {
            return formatBytes(readBytes);
        }

        public String writeFormatted() {
            return formatBytes(writeBytes);
        }

        private static String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return "%.1f MB".formatted(bytes / (1024.0 * 1024));
            return "%.2f GB".formatted(bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Returns process uptime.
     */
    public java.time.Duration uptime() {
        if (startTime == null) return java.time.Duration.ZERO;
        return java.time.Duration.between(startTime, Instant.now());
    }

    /**
     * Returns formatted uptime.
     */
    public String uptimeFormatted() {
        var duration = uptime();
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (days > 0) {
            return "%dd %dh %dm".formatted(days, hours, minutes);
        } else if (hours > 0) {
            return "%dh %dm %ds".formatted(hours, minutes, seconds);
        } else if (minutes > 0) {
            return "%dm %ds".formatted(minutes, seconds);
        } else {
            return "%ds".formatted(seconds);
        }
    }

    /**
     * Creates a minimal sample for a process.
     */
    public static ProcessSample minimal(long pid, String name, String user) {
        return new ProcessSample(
            pid, name, "", user,
            ProcessState.UNKNOWN,
            CpuMetrics.zero(),
            MemoryMetrics.zero(),
            IoMetrics.zero(),
            0, 0, null, Instant.now()
        );
    }

    /**
     * Checks if this is a Java/JVM process.
     */
    public boolean isJavaProcess() {
        String lowerName = name.toLowerCase();
        String lowerCmd = commandLine.toLowerCase();
        return lowerName.equals("java") ||
               lowerName.startsWith("java.") ||
               lowerCmd.contains("java") ||
               lowerCmd.contains("-jar");
    }
}
