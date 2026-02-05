package org.iumotionlabs.hefesto.feature.procwatch.service;

import org.iumotionlabs.hefesto.feature.procwatch.model.*;
import org.iumotionlabs.hefesto.feature.procwatch.sampler.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Main service for process monitoring.
 * Orchestrates sampling, alert evaluation, and optional JVM monitoring.
 */
public final class ProcessMonitorService {

    private final ProcessSampler sampler;
    private final AlertEngine alertEngine;
    private final JmxService jmxService;
    private final AlertParser alertParser;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "process-monitor");
        // Don't use daemon thread - we need it to complete its work
        t.setDaemon(false);
        return t;
    });

    private volatile ScheduledFuture<?> monitoringTask;
    private volatile boolean running = false;

    public ProcessMonitorService() {
        this.sampler = createSampler();
        this.alertEngine = new AlertEngine();
        this.jmxService = new JmxService();
        this.alertParser = new AlertParser();
    }

    /**
     * Gets a single sample for a process by PID.
     */
    public Optional<ProcessSample> sampleByPid(long pid) {
        return sampler.sampleByPid(pid);
    }

    /**
     * Gets samples for all processes matching a name pattern.
     */
    public List<ProcessSample> sampleByName(String namePattern) {
        return sampler.sampleByName(namePattern);
    }

    /**
     * Gets samples for all processes matching a command pattern.
     */
    public List<ProcessSample> sampleByCommand(String commandPattern) {
        return sampler.sampleByCommand(commandPattern);
    }

    /**
     * Gets top processes by CPU.
     */
    public List<ProcessSample> topByCpu(int limit) {
        return sampler.topByCpu(limit);
    }

    /**
     * Gets top processes by memory.
     */
    public List<ProcessSample> topByMemory(int limit) {
        return sampler.topByMemory(limit);
    }

    /**
     * Gets all running processes.
     */
    public List<ProcessSample> getAllProcesses() {
        return sampler.getAllProcesses();
    }

    /**
     * Gets system info.
     */
    public SystemInfo getSystemInfo() {
        return new SystemInfo(
            sampler.getTotalMemoryBytes(),
            sampler.getCpuCount(),
            System.getProperty("os.name"),
            System.getProperty("os.version")
        );
    }

    /**
     * Gets JVM metrics for a process (if it's a Java process with JMX).
     */
    public Optional<JvmMetrics> getJvmMetrics(long pid) {
        Optional<ProcessSample> sample = sampleByPid(pid);
        if (sample.isEmpty() || !sample.get().isJavaProcess()) {
            return Optional.empty();
        }

        JvmMetrics metrics = jmxService.getMetrics(pid);
        return metrics.heap().usedBytes() > 0 ? Optional.of(metrics) : Optional.empty();
    }

    /**
     * Parses alert rules from expressions.
     */
    public List<AlertRule> parseAlerts(List<String> expressions) {
        List<AlertRule> rules = new ArrayList<>();
        for (String expr : expressions) {
            rules.add(alertParser.parse(expr));
        }
        return rules;
    }

    /**
     * Evaluates alerts for a single sample.
     */
    public List<AlertResult> evaluateAlerts(ProcessSample sample, List<AlertRule> rules) {
        return alertEngine.evaluate(sample, rules);
    }

    /**
     * Starts continuous monitoring of a process.
     *
     * @param pid      the process ID
     * @param interval sampling interval
     * @param rules    alert rules to evaluate
     * @param onSample callback for each sample
     * @param onAlert  callback for triggered alerts
     */
    public void startMonitoring(long pid, Duration interval, List<AlertRule> rules,
                                Consumer<ProcessSample> onSample,
                                Consumer<AlertResult> onAlert) {
        if (running) {
            stopMonitoring();
        }

        running = true;
        monitoringTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                Optional<ProcessSample> sample = sampleByPid(pid);
                if (sample.isEmpty()) {
                    return;
                }

                ProcessSample s = sample.get();
                onSample.accept(s);

                if (!rules.isEmpty()) {
                    List<AlertResult> results = alertEngine.evaluate(s, rules);
                    for (AlertResult result : results) {
                        if (result.triggered()) {
                            onAlert.accept(result);
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore sampling errors
            }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Starts continuous monitoring of processes by name.
     */
    public void startMonitoringByName(String namePattern, String commandMatch,
                                      Duration interval, List<AlertRule> rules,
                                      Consumer<ProcessSample> onSample,
                                      Consumer<AlertResult> onAlert) {
        if (running) {
            stopMonitoring();
        }

        running = true;
        monitoringTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                List<ProcessSample> samples = sampleByName(namePattern);
                if (commandMatch != null && !commandMatch.isEmpty()) {
                    samples = samples.stream()
                        .filter(s -> s.commandLine().toLowerCase().contains(commandMatch.toLowerCase()))
                        .toList();
                }

                for (ProcessSample s : samples) {
                    onSample.accept(s);

                    if (!rules.isEmpty()) {
                        List<AlertResult> results = alertEngine.evaluate(s, rules);
                        for (AlertResult result : results) {
                            if (result.triggered()) {
                                onAlert.accept(result);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore sampling errors
            }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Starts top processes monitoring.
     */
    public void startTopMonitoring(TopMode mode, int limit, Duration interval,
                                   Consumer<List<ProcessSample>> onSamples) {
        startTopMonitoring(mode, limit, interval, onSamples, null);
    }

    /**
     * Starts top processes monitoring with error callback.
     */
    public void startTopMonitoring(TopMode mode, int limit, Duration interval,
                                   Consumer<List<ProcessSample>> onSamples,
                                   Consumer<Exception> onError) {
        if (running) {
            stopMonitoring();
        }

        running = true;
        long intervalMs = interval.toMillis();
        monitoringTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                List<ProcessSample> samples = switch (mode) {
                    case CPU -> topByCpu(limit);
                    case MEMORY -> topByMemory(limit);
                };
                onSamples.accept(samples);
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(e);
                }
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops monitoring.
     */
    public void stopMonitoring() {
        running = false;
        if (monitoringTask != null) {
            monitoringTask.cancel(true);
            monitoringTask = null;
        }
        alertEngine.clearAllHistory();
    }

    /**
     * Checks if monitoring is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Executes a dump command on alert breach.
     */
    public String executeDumpCommand(long pid, DumpType type) {
        return switch (type) {
            case JSTACK -> executeCommand("jstack", String.valueOf(pid));
            case JMAP -> executeCommand("jmap", "-histo", String.valueOf(pid));
            case PSTACK -> executeCommand("pstack", String.valueOf(pid));
            case LSOF -> executeCommand("lsof", "-p", String.valueOf(pid));
        };
    }

    /**
     * Shuts down the service.
     */
    public void shutdown() {
        stopMonitoring();
        jmxService.closeAll();
        scheduler.shutdownNow();
    }

    private ProcessSampler createSampler() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            return new MacOsProcessSampler();
        } else if (os.contains("win")) {
            return new WindowsProcessSampler();
        } else {
            return new LinuxProcessSampler();
        }
    }

    private String executeCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true).start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor(30, TimeUnit.SECONDS);
            return output.toString();
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    public enum TopMode {
        CPU, MEMORY
    }

    public enum DumpType {
        JSTACK, JMAP, PSTACK, LSOF
    }

    public record SystemInfo(
        long totalMemoryBytes,
        int cpuCount,
        String osName,
        String osVersion
    ) {
        public String totalMemoryFormatted() {
            long gb = totalMemoryBytes / (1024 * 1024 * 1024);
            return gb + " GB";
        }
    }
}
