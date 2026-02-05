package org.iumotionlabs.hefesto.feature.procwatch.sampler;

import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;
import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.*;

/**
 * Windows implementation of ProcessSampler using wmic and PowerShell.
 */
public final class WindowsProcessSampler implements ProcessSampler {

    private long totalMemoryBytes = -1;
    private int cpuCount = -1;

    public WindowsProcessSampler() {
        initSystemInfo();
    }

    private void initSystemInfo() {
        try {
            // Get total memory via wmic
            Process process = new ProcessBuilder(
                "wmic", "OS", "get", "TotalVisibleMemorySize", "/value"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("TotalVisibleMemorySize=")) {
                        totalMemoryBytes = Long.parseLong(line.split("=")[1].trim()) * 1024;
                        break;
                    }
                }
            }
            process.waitFor();

            // Get CPU count
            cpuCount = Runtime.getRuntime().availableProcessors();

        } catch (Exception e) {
            totalMemoryBytes = Runtime.getRuntime().maxMemory();
            cpuCount = Runtime.getRuntime().availableProcessors();
        }
    }

    @Override
    public Optional<ProcessSample> sampleByPid(long pid) {
        try {
            Process process = new ProcessBuilder(
                "wmic", "process", "where", "ProcessId=" + pid, "get",
                "ProcessId,Name,CommandLine,WorkingSetSize,VirtualSize,ThreadCount,UserModeTime,KernelModeTime",
                "/format:csv"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // Skip empty lines and header
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(String.valueOf(pid))) {
                        return parseWmicLine(line);
                    }
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
                "wmic", "process", "get",
                "ProcessId,Name,CommandLine,WorkingSetSize,VirtualSize,ThreadCount,UserModeTime,KernelModeTime",
                "/format:csv"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean headerSkipped = false;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    if (!headerSkipped && line.contains("ProcessId")) {
                        headerSkipped = true;
                        continue;
                    }
                    parseWmicLine(line).ifPresent(samples::add);
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

    private Optional<ProcessSample> parseWmicLine(String line) {
        // CSV format: Node,CommandLine,KernelModeTime,Name,ProcessId,ThreadCount,UserModeTime,VirtualSize,WorkingSetSize
        try {
            String[] parts = line.split(",");
            if (parts.length < 9) {
                return Optional.empty();
            }

            String commandLine = parts[1];
            long kernelModeTime = parseLong(parts[2]);
            String name = parts[3];
            long pid = parseLong(parts[4]);
            int threadCount = (int) parseLong(parts[5]);
            long userModeTime = parseLong(parts[6]);
            long virtualSize = parseLong(parts[7]);
            long workingSetSize = parseLong(parts[8]);

            if (pid == 0) {
                return Optional.empty(); // Skip System Idle Process
            }

            // Calculate CPU time in ms (100-nanosecond units to ms)
            long totalCpuTimeMs = (userModeTime + kernelModeTime) / 10000;

            // CPU percentage is difficult to calculate accurately without delta
            double cpuPercent = 0; // Would need sampling over time

            double memPercent = (double) workingSetSize / totalMemoryBytes * 100;

            ProcessSample sample = new ProcessSample(
                pid,
                name,
                commandLine,
                "", // User not easily available from wmic
                ProcessState.RUNNING, // Simplified
                new CpuMetrics(cpuPercent, cpuPercent, userModeTime / 10000, kernelModeTime / 10000, totalCpuTimeMs),
                new MemoryMetrics(workingSetSize, virtualSize, 0, memPercent),
                IoMetrics.zero(), // Would need separate query
                threadCount,
                0, // FD count not easily available on Windows
                null,
                Instant.now()
            );

            return Optional.of(sample);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private long parseLong(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
