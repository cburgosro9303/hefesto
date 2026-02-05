package org.iumotionlabs.hefesto.feature.procwatch.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Record representing JVM-specific metrics obtained via JMX.
 */
public record JvmMetrics(
    long pid,
    HeapMetrics heap,
    NonHeapMetrics nonHeap,
    ThreadMetrics threads,
    GcMetrics gc,
    ClassLoadingMetrics classLoading,
    RuntimeInfo runtime,
    Instant sampleTime
) {
    /**
     * Heap memory metrics.
     */
    public record HeapMetrics(
        long usedBytes,
        long committedBytes,
        long maxBytes,
        double usedPercent
    ) {
        public String usedFormatted() {
            return formatBytes(usedBytes);
        }

        public String maxFormatted() {
            return formatBytes(maxBytes);
        }

        public String percentFormatted() {
            return "%.1f%%".formatted(usedPercent);
        }

        public static HeapMetrics empty() {
            return new HeapMetrics(0, 0, 0, 0);
        }
    }

    /**
     * Non-heap memory metrics (Metaspace, Code Cache, etc.).
     */
    public record NonHeapMetrics(
        long usedBytes,
        long committedBytes
    ) {
        public String usedFormatted() {
            return formatBytes(usedBytes);
        }

        public static NonHeapMetrics empty() {
            return new NonHeapMetrics(0, 0);
        }
    }

    /**
     * Thread metrics.
     */
    public record ThreadMetrics(
        int liveThreads,
        int daemonThreads,
        int peakThreads,
        long totalStarted,
        int deadlockedCount
    ) {
        public boolean hasDeadlock() {
            return deadlockedCount > 0;
        }

        public static ThreadMetrics empty() {
            return new ThreadMetrics(0, 0, 0, 0, 0);
        }
    }

    /**
     * Garbage collection metrics.
     */
    public record GcMetrics(
        List<GcCollector> collectors,
        long totalCollections,
        long totalTimeMs
    ) {
        public record GcCollector(
            String name,
            long collectionCount,
            long collectionTimeMs
        ) {
            public String avgTimeFormatted() {
                if (collectionCount == 0) return "0ms";
                return "%.1fms".formatted((double) collectionTimeMs / collectionCount);
            }
        }

        public double avgPauseMs() {
            if (totalCollections == 0) return 0;
            return (double) totalTimeMs / totalCollections;
        }

        public String avgPauseFormatted() {
            return "%.1fms".formatted(avgPauseMs());
        }

        public static GcMetrics empty() {
            return new GcMetrics(List.of(), 0, 0);
        }
    }

    /**
     * Class loading metrics.
     */
    public record ClassLoadingMetrics(
        int loadedClasses,
        long totalLoadedClasses,
        long unloadedClasses
    ) {
        public static ClassLoadingMetrics empty() {
            return new ClassLoadingMetrics(0, 0, 0);
        }
    }

    /**
     * JVM runtime information.
     */
    public record RuntimeInfo(
        String vmName,
        String vmVersion,
        String vmVendor,
        long uptimeMs,
        List<String> inputArguments
    ) {
        public String uptimeFormatted() {
            long seconds = uptimeMs / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
                return "%dd %dh %dm".formatted(days, hours % 24, minutes % 60);
            } else if (hours > 0) {
                return "%dh %dm".formatted(hours, minutes % 60);
            } else if (minutes > 0) {
                return "%dm %ds".formatted(minutes, seconds % 60);
            } else {
                return "%ds".formatted(seconds);
            }
        }

        public static RuntimeInfo empty() {
            return new RuntimeInfo("", "", "", 0, List.of());
        }
    }

    /**
     * Returns total memory used (heap + non-heap).
     */
    public long totalUsedBytes() {
        return heap.usedBytes() + nonHeap.usedBytes();
    }

    /**
     * Creates an empty JVM metrics object.
     */
    public static JvmMetrics empty(long pid) {
        return new JvmMetrics(
            pid,
            HeapMetrics.empty(),
            NonHeapMetrics.empty(),
            ThreadMetrics.empty(),
            GcMetrics.empty(),
            ClassLoadingMetrics.empty(),
            RuntimeInfo.empty(),
            Instant.now()
        );
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return "%.1f MB".formatted(bytes / (1024.0 * 1024));
        return "%.2f GB".formatted(bytes / (1024.0 * 1024 * 1024));
    }
}
