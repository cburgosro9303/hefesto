package org.iumotionlabs.hefesto.feature.procwatch.sampler;

import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;

import java.util.List;
import java.util.Optional;

/**
 * Interface for sampling process metrics.
 * Sealed to control platform-specific implementations.
 */
public sealed interface ProcessSampler
    permits LinuxProcessSampler, MacOsProcessSampler, WindowsProcessSampler {

    /**
     * Samples a process by PID.
     *
     * @param pid the process ID
     * @return the process sample, or empty if not found
     */
    Optional<ProcessSample> sampleByPid(long pid);

    /**
     * Samples all processes matching a name pattern.
     *
     * @param namePattern the process name pattern (case-insensitive)
     * @return list of matching process samples
     */
    List<ProcessSample> sampleByName(String namePattern);

    /**
     * Samples all processes matching a command line pattern.
     *
     * @param commandPattern the command line pattern (case-insensitive)
     * @return list of matching process samples
     */
    List<ProcessSample> sampleByCommand(String commandPattern);

    /**
     * Gets top N processes by CPU usage.
     *
     * @param limit the maximum number of processes
     * @return list of processes sorted by CPU descending
     */
    List<ProcessSample> topByCpu(int limit);

    /**
     * Gets top N processes by memory usage.
     *
     * @param limit the maximum number of processes
     * @return list of processes sorted by RSS descending
     */
    List<ProcessSample> topByMemory(int limit);

    /**
     * Gets all running processes.
     *
     * @return list of all process samples
     */
    List<ProcessSample> getAllProcesses();

    /**
     * Gets the total system memory in bytes.
     */
    long getTotalMemoryBytes();

    /**
     * Gets the number of CPU cores.
     */
    int getCpuCount();
}
