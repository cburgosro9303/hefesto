package org.iumotionlabs.hefesto.feature.portinfo.service;

import org.iumotionlabs.hefesto.feature.portinfo.model.ProcessInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for enriching process information with extended details.
 */
public final class ProcessEnrichmentService {

    private final PortInfoService.OperatingSystem os;

    public ProcessEnrichmentService() {
        this.os = PortInfoService.OperatingSystem.current();
    }

    /**
     * Gets extended process information for a PID.
     */
    public Optional<ProcessInfo> getProcessInfo(long pid) {
        return switch (os) {
            case LINUX -> getLinuxProcessInfo(pid);
            case MACOS -> getMacOsProcessInfo(pid);
            case WINDOWS -> getWindowsProcessInfo(pid);
            case UNKNOWN -> Optional.empty();
        };
    }

    private Optional<ProcessInfo> getLinuxProcessInfo(long pid) {
        try {
            // Use ps command to get process info
            Process process = new ProcessBuilder(
                "ps", "-p", String.valueOf(pid), "-o", "comm=,user=,rss=,vsz=,time=,nlwp=,lstart="
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return parseLinuxPsOutput(pid, line);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Fallback to basic info
        }

        return Optional.empty();
    }

    private Optional<ProcessInfo> parseLinuxPsOutput(long pid, String line) {
        // Format: name user rss vsz time nlwp lstart
        String[] parts = line.trim().split("\\s+", 7);
        if (parts.length >= 6) {
            try {
                String name = parts[0];
                String user = parts[1];
                long rssKb = Long.parseLong(parts[2]);
                long vszKb = Long.parseLong(parts[3]);
                long cpuTimeMs = parseCpuTime(parts[4]);
                int threadCount = Integer.parseInt(parts[5]);
                Instant startTime = parts.length > 6 ? parseLinuxLstart(parts[6]) : null;

                String cwd = getWorkingDirectory(pid);
                String cmdLine = getCommandLine(pid);

                return Optional.of(new ProcessInfo(
                    pid, name, cmdLine, user, cwd, rssKb, vszKb, cpuTimeMs, threadCount, startTime
                ));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<ProcessInfo> getMacOsProcessInfo(long pid) {
        try {
            // Use ps command to get process info
            Process process = new ProcessBuilder(
                "ps", "-p", String.valueOf(pid), "-o", "comm=,user=,rss=,vsz=,time=,lstart="
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return parseMacOsPsOutput(pid, line);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Fallback to basic info
        }

        return Optional.empty();
    }

    private Optional<ProcessInfo> parseMacOsPsOutput(long pid, String line) {
        // Format varies on macOS, using simpler parsing
        Pattern pattern = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+(\\S+)\\s+(.*)$");
        Matcher m = pattern.matcher(line.trim());

        if (m.find()) {
            try {
                String name = m.group(1);
                String user = m.group(2);
                long rssKb = Long.parseLong(m.group(3));
                long vszKb = Long.parseLong(m.group(4));
                long cpuTimeMs = parseCpuTime(m.group(5));
                Instant startTime = parseMacOsLstart(m.group(6));

                String cwd = getWorkingDirectory(pid);
                String cmdLine = getCommandLine(pid);
                int threadCount = getThreadCount(pid);

                return Optional.of(new ProcessInfo(
                    pid, name, cmdLine, user, cwd, rssKb, vszKb, cpuTimeMs, threadCount, startTime
                ));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<ProcessInfo> getWindowsProcessInfo(long pid) {
        try {
            // Use WMIC to get process info
            Process process = new ProcessBuilder(
                "wmic", "process", "where", "ProcessId=" + pid, "get",
                "Name,CommandLine,WorkingSetSize,VirtualSize,UserModeTime,KernelModeTime,ThreadCount,CreationDate",
                "/format:csv"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // Skip header
                reader.readLine();
                reader.readLine();
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return parseWindowsWmicOutput(pid, line);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Fallback
        }

        return Optional.empty();
    }

    private Optional<ProcessInfo> parseWindowsWmicOutput(long pid, String line) {
        // CSV format: Node,CommandLine,CreationDate,KernelModeTime,Name,ThreadCount,UserModeTime,VirtualSize,WorkingSetSize
        String[] parts = line.split(",");
        if (parts.length >= 9) {
            try {
                String cmdLine = parts[1];
                String name = parts[4];
                int threadCount = Integer.parseInt(parts[5].trim());
                long userModeTime = Long.parseLong(parts[6].trim());
                long kernelModeTime = Long.parseLong(parts[3].trim());
                long vszBytes = Long.parseLong(parts[7].trim());
                long rssBytes = Long.parseLong(parts[8].trim());

                long cpuTimeMs = (userModeTime + kernelModeTime) / 10000; // 100ns to ms
                long rssKb = rssBytes / 1024;
                long vszKb = vszBytes / 1024;

                return Optional.of(new ProcessInfo(
                    pid, name, cmdLine, "", "", rssKb, vszKb, cpuTimeMs, threadCount, null
                ));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private long parseCpuTime(String time) {
        // Format: HH:MM:SS or MM:SS
        String[] parts = time.split(":");
        try {
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2].split("\\.")[0]);
                return (hours * 3600L + minutes * 60L + seconds) * 1000L;
            } else if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1].split("\\.")[0]);
                return (minutes * 60L + seconds) * 1000L;
            }
        } catch (NumberFormatException e) {
            // Ignore
        }
        return 0;
    }

    private Instant parseLinuxLstart(String lstart) {
        // Format: Day Mon DD HH:MM:SS YYYY
        // This is complex to parse, simplified version
        return null;
    }

    private Instant parseMacOsLstart(String lstart) {
        // Similar complex format
        return null;
    }

    private String getWorkingDirectory(long pid) {
        try {
            if (os == PortInfoService.OperatingSystem.LINUX) {
                Process process = new ProcessBuilder("readlink", "-f", "/proc/" + pid + "/cwd")
                    .redirectErrorStream(true).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    return line != null ? line.trim() : "";
                }
            } else if (os == PortInfoService.OperatingSystem.MACOS) {
                Process process = new ProcessBuilder("lsof", "-p", String.valueOf(pid), "-Fn")
                    .redirectErrorStream(true).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("n") && line.contains("cwd")) {
                            return line.substring(1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return "";
    }

    private String getCommandLine(long pid) {
        try {
            if (os == PortInfoService.OperatingSystem.LINUX) {
                Process process = new ProcessBuilder("cat", "/proc/" + pid + "/cmdline")
                    .redirectErrorStream(true).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    return line != null ? line.replace('\0', ' ').trim() : "";
                }
            } else if (os == PortInfoService.OperatingSystem.MACOS) {
                Process process = new ProcessBuilder("ps", "-p", String.valueOf(pid), "-o", "command=")
                    .redirectErrorStream(true).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    return line != null ? line.trim() : "";
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return "";
    }

    private int getThreadCount(long pid) {
        try {
            if (os == PortInfoService.OperatingSystem.MACOS) {
                Process process = new ProcessBuilder("ps", "-M", "-p", String.valueOf(pid))
                    .redirectErrorStream(true).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    int count = 0;
                    while (reader.readLine() != null) {
                        count++;
                    }
                    return Math.max(0, count - 1); // Subtract header
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }
}
