package org.iumotionlabs.hefesto.feature.procwatch.sampler;

import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;
import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Linux implementation of ProcessSampler using /proc filesystem and ps command.
 */
public final class LinuxProcessSampler implements ProcessSampler {

    private long totalMemoryBytes = -1;
    private int cpuCount = -1;
    private long lastCpuTotal = 0;
    private Map<Long, Long> lastProcessCpuTime = new HashMap<>();

    public LinuxProcessSampler() {
        initSystemInfo();
    }

    private void initSystemInfo() {
        try {
            // Read from /proc/meminfo
            List<String> lines = Files.readAllLines(Path.of("/proc/meminfo"));
            for (String line : lines) {
                if (line.startsWith("MemTotal:")) {
                    String[] parts = line.split("\\s+");
                    totalMemoryBytes = Long.parseLong(parts[1]) * 1024; // kB to bytes
                    break;
                }
            }

            // Read CPU count from /proc/cpuinfo
            cpuCount = (int) Files.lines(Path.of("/proc/cpuinfo"))
                .filter(line -> line.startsWith("processor"))
                .count();

        } catch (Exception e) {
            totalMemoryBytes = Runtime.getRuntime().maxMemory();
            cpuCount = Runtime.getRuntime().availableProcessors();
        }
    }

    @Override
    public Optional<ProcessSample> sampleByPid(long pid) {
        Path procPath = Path.of("/proc", String.valueOf(pid));
        if (!Files.exists(procPath)) {
            return Optional.empty();
        }

        try {
            return Optional.of(sampleFromProc(pid));
        } catch (Exception e) {
            return Optional.empty();
        }
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
            // Use ps for easier parsing
            Process process = new ProcessBuilder(
                "ps", "-eo", "pid,user,stat,%cpu,%mem,rss,vsz,nlwp,command", "--no-headers"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parsePsLine(line).ifPresent(samples::add);
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

    private ProcessSample sampleFromProc(long pid) throws Exception {
        Path procPath = Path.of("/proc", String.valueOf(pid));

        // Read stat file
        String statContent = Files.readString(procPath.resolve("stat"));
        String[] statParts = parseStatFile(statContent);

        // Read status file for more info
        Map<String, String> status = parseStatusFile(procPath.resolve("status"));

        // Read cmdline
        String cmdline = Files.readString(procPath.resolve("cmdline"))
            .replace('\0', ' ').trim();

        // Parse values
        String name = statParts[1].replaceAll("[()]", "");
        String stateCode = statParts[2];
        long utime = Long.parseLong(statParts[13]);
        long stime = Long.parseLong(statParts[14]);
        int numThreads = Integer.parseInt(statParts[19]);
        long starttime = Long.parseLong(statParts[21]);

        long rssPages = Long.parseLong(statParts[23]);
        long rssBytes = rssPages * 4096; // Page size typically 4KB

        long vsize = Long.parseLong(statParts[22]);

        String user = status.getOrDefault("Uid", "0").split("\\s+")[0];

        // Get open file descriptors
        int openFds = getOpenFileDescriptors(pid);

        // Calculate CPU percentage (simplified)
        long totalCpuTime = utime + stime;
        double cpuPercent = calculateCpuPercent(pid, totalCpuTime);

        // Read I/O stats if available
        IoMetrics ioMetrics = readIoStats(procPath);

        double memPercent = (double) rssBytes / totalMemoryBytes * 100;

        return new ProcessSample(
            pid,
            name,
            cmdline.isEmpty() ? name : cmdline,
            user,
            ProcessState.fromCode(stateCode),
            new CpuMetrics(cpuPercent, cpuPercent, utime * 10, stime * 10, (utime + stime) * 10),
            new MemoryMetrics(rssBytes, vsize, 0, memPercent),
            ioMetrics,
            numThreads,
            openFds,
            null,
            Instant.now()
        );
    }

    private String[] parseStatFile(String content) {
        // Handle process names with spaces/parentheses
        int start = content.indexOf('(');
        int end = content.lastIndexOf(')');

        if (start < 0 || end < 0) {
            return content.split("\\s+");
        }

        String pid = content.substring(0, start).trim();
        String name = content.substring(start, end + 1);
        String rest = content.substring(end + 1).trim();

        String[] restParts = rest.split("\\s+");
        String[] result = new String[restParts.length + 2];
        result[0] = pid;
        result[1] = name;
        System.arraycopy(restParts, 0, result, 2, restParts.length);

        return result;
    }

    private Map<String, String> parseStatusFile(Path path) {
        Map<String, String> status = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path)) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    status.put(line.substring(0, colon).trim(),
                              line.substring(colon + 1).trim());
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return status;
    }

    private double calculateCpuPercent(long pid, long currentCpuTime) {
        Long lastTime = lastProcessCpuTime.get(pid);
        lastProcessCpuTime.put(pid, currentCpuTime);

        if (lastTime == null) {
            return 0;
        }

        // This is a simplified calculation
        // For accurate CPU%, we'd need to track system-wide CPU time delta
        long delta = currentCpuTime - lastTime;
        return Math.min(100, delta * 0.1); // Rough approximation
    }

    private int getOpenFileDescriptors(long pid) {
        try {
            Path fdPath = Path.of("/proc", String.valueOf(pid), "fd");
            if (Files.exists(fdPath)) {
                return (int) Files.list(fdPath).count();
            }
        } catch (Exception e) {
            // Permission denied or process gone
        }
        return 0;
    }

    private IoMetrics readIoStats(Path procPath) {
        try {
            Path ioPath = procPath.resolve("io");
            if (!Files.exists(ioPath)) {
                return IoMetrics.zero();
            }

            long readBytes = 0, writeBytes = 0;
            for (String line : Files.readAllLines(ioPath)) {
                if (line.startsWith("read_bytes:")) {
                    readBytes = Long.parseLong(line.split(":")[1].trim());
                } else if (line.startsWith("write_bytes:")) {
                    writeBytes = Long.parseLong(line.split(":")[1].trim());
                }
            }

            return new IoMetrics(readBytes, writeBytes, 0, 0);
        } catch (Exception e) {
            return IoMetrics.zero();
        }
    }

    /**
     * Parses a ps output line for bulk listing (skips expensive per-process lookups).
     */
    private Optional<ProcessSample> parsePsLine(String line) {
        try {
            String[] parts = line.trim().split("\\s+", 9);
            if (parts.length < 9) {
                return Optional.empty();
            }

            long pid = Long.parseLong(parts[0]);
            String user = parts[1];
            String stat = parts[2];
            double cpuPercent = Double.parseDouble(parts[3]);
            double memPercent = Double.parseDouble(parts[4]);
            long rssKb = Long.parseLong(parts[5]);
            long vszKb = Long.parseLong(parts[6]);
            int threads = Integer.parseInt(parts[7]);
            String command = parts[8];

            String processName = extractProcessName(command);

            return Optional.of(new ProcessSample(
                pid, processName, command, user,
                ProcessState.fromCode(stat),
                new CpuMetrics(cpuPercent, cpuPercent, 0, 0, 0),
                new MemoryMetrics(rssKb * 1024, vszKb * 1024, 0, memPercent),
                IoMetrics.zero(),
                threads, 0, null, Instant.now()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String extractProcessName(String command) {
        if (command == null || command.isEmpty()) return "unknown";

        String name = command;
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < name.length() - 1) {
            name = name.substring(lastSlash + 1);
        }

        int space = name.indexOf(' ');
        if (space > 0) {
            name = name.substring(0, space);
        }

        return name;
    }
}
