package org.iumotionlabs.hefesto.feature.procwatch.sampler;

import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;
import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.*;

/**
 * macOS implementation of ProcessSampler using ps and lsof commands.
 */
public final class MacOsProcessSampler implements ProcessSampler {

    private long totalMemoryBytes = -1;
    private int cpuCount = -1;

    public MacOsProcessSampler() {
        initSystemInfo();
    }

    private void initSystemInfo() {
        try {
            // Get total memory
            Process process = new ProcessBuilder("sysctl", "-n", "hw.memsize")
                .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    totalMemoryBytes = Long.parseLong(line.trim());
                }
            }
            process.waitFor();

            // Get CPU count
            process = new ProcessBuilder("sysctl", "-n", "hw.ncpu")
                .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    cpuCount = Integer.parseInt(line.trim());
                }
            }
            process.waitFor();
        } catch (Exception e) {
            totalMemoryBytes = Runtime.getRuntime().maxMemory();
            cpuCount = Runtime.getRuntime().availableProcessors();
        }
    }

    @Override
    public Optional<ProcessSample> sampleByPid(long pid) {
        try {
            Process process = new ProcessBuilder(
                "ps", "-p", String.valueOf(pid), "-o", "pid,user,stat,%cpu,%mem,rss,vsz,command"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // Skip header
                reader.readLine();
                String line = reader.readLine();
                if (line != null) {
                    return parsePsLineDetailed(line, pid);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Process not found or error
        }
        return Optional.empty();
    }

    @Override
    public List<ProcessSample> sampleByName(String namePattern) {
        List<ProcessSample> samples = new ArrayList<>();
        String lowerPattern = namePattern.toLowerCase();

        for (ProcessSample sample : getAllProcesses()) {
            if (sample.name().toLowerCase().contains(lowerPattern)) {
                samples.add(sample);
            }
        }

        return samples;
    }

    @Override
    public List<ProcessSample> sampleByCommand(String commandPattern) {
        List<ProcessSample> samples = new ArrayList<>();
        String lowerPattern = commandPattern.toLowerCase();

        for (ProcessSample sample : getAllProcesses()) {
            if (sample.commandLine().toLowerCase().contains(lowerPattern)) {
                samples.add(sample);
            }
        }

        return samples;
    }

    @Override
    public List<ProcessSample> topByCpu(int limit) {
        List<ProcessSample> all = getAllProcesses();
        all.sort((a, b) -> Double.compare(b.cpu().percentInstant(), a.cpu().percentInstant()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    @Override
    public List<ProcessSample> topByMemory(int limit) {
        List<ProcessSample> all = getAllProcesses();
        all.sort((a, b) -> Long.compare(b.memory().rssBytes(), a.memory().rssBytes()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    @Override
    public List<ProcessSample> getAllProcesses() {
        List<ProcessSample> samples = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(
                "ps", "-ax", "-o", "pid,user,stat,%cpu,%mem,rss,vsz,command"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // Skip header
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    parsePsLineFast(line).ifPresent(samples::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Error reading processes
        }

        return samples;
    }

    @Override
    public long getTotalMemoryBytes() {
        return totalMemoryBytes > 0 ? totalMemoryBytes : Runtime.getRuntime().maxMemory();
    }

    @Override
    public int getCpuCount() {
        return cpuCount > 0 ? cpuCount : Runtime.getRuntime().availableProcessors();
    }

    /**
     * Parses a ps output line for a single PID query (includes expensive FD/thread lookups).
     */
    private Optional<ProcessSample> parsePsLineDetailed(String line, long expectedPid) {
        try {
            // Format: PID USER STAT %CPU %MEM RSS VSZ COMMAND
            String[] parts = line.trim().split("\\s+", 8);
            if (parts.length < 8) {
                return Optional.empty();
            }

            long pid = Long.parseLong(parts[0]);
            if (pid != expectedPid) return Optional.empty();

            String user = parts[1];
            String stat = parts[2];
            double cpuPercent = Double.parseDouble(parts[3]);
            double memPercent = Double.parseDouble(parts[4]);
            long rssKb = Long.parseLong(parts[5]);
            long vszKb = Long.parseLong(parts[6]);
            String command = parts[7];

            String processName = extractProcessName(command);

            // Only fetch expensive metrics for single-PID queries
            int openFds = getOpenFileDescriptors(pid);
            int threadCount = getThreadCount(pid);

            return Optional.of(new ProcessSample(
                pid, processName, command, user,
                ProcessState.fromCode(stat),
                new CpuMetrics(cpuPercent, cpuPercent, 0, 0, 0),
                new MemoryMetrics(rssKb * 1024, vszKb * 1024, 0, memPercent),
                IoMetrics.zero(),
                threadCount, openFds, null, Instant.now()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Parses a ps output line for bulk listing (skips expensive per-process calls).
     */
    private Optional<ProcessSample> parsePsLineFast(String line) {
        try {
            // Format: PID USER STAT %CPU %MEM RSS VSZ COMMAND
            String[] parts = line.trim().split("\\s+", 8);
            if (parts.length < 8) {
                return Optional.empty();
            }

            long pid = Long.parseLong(parts[0]);
            String user = parts[1];
            String stat = parts[2];
            double cpuPercent = Double.parseDouble(parts[3]);
            double memPercent = Double.parseDouble(parts[4]);
            long rssKb = Long.parseLong(parts[5]);
            long vszKb = Long.parseLong(parts[6]);
            String command = parts[7];

            String processName = extractProcessName(command);

            return Optional.of(new ProcessSample(
                pid, processName, command, user,
                ProcessState.fromCode(stat),
                new CpuMetrics(cpuPercent, cpuPercent, 0, 0, 0),
                new MemoryMetrics(rssKb * 1024, vszKb * 1024, 0, memPercent),
                IoMetrics.zero(),
                0, 0, null, Instant.now()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String extractProcessName(String command) {
        if (command == null || command.isEmpty()) return "unknown";

        // Remove path
        String name = command;
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < name.length() - 1) {
            name = name.substring(lastSlash + 1);
        }

        // Get first word (before space)
        int space = name.indexOf(' ');
        if (space > 0) {
            name = name.substring(0, space);
        }

        return name;
    }

    private int getOpenFileDescriptors(long pid) {
        try {
            Process process = new ProcessBuilder("lsof", "-p", String.valueOf(pid))
                .redirectErrorStream(true).start();

            int count = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    count++;
                }
            }

            process.waitFor();
            return Math.max(0, count - 1); // Subtract header
        } catch (Exception e) {
            return 0;
        }
    }

    private int getThreadCount(long pid) {
        try {
            Process process = new ProcessBuilder("ps", "-M", "-p", String.valueOf(pid))
                .redirectErrorStream(true).start();

            int count = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    count++;
                }
            }

            process.waitFor();
            return Math.max(0, count - 1); // Subtract header
        } catch (Exception e) {
            return 0;
        }
    }
}
