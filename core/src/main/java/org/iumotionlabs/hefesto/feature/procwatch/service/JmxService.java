package org.iumotionlabs.hefesto.feature.procwatch.service;

import org.iumotionlabs.hefesto.feature.procwatch.model.JvmMetrics;
import org.iumotionlabs.hefesto.feature.procwatch.model.JvmMetrics.*;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.remote.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.*;
import java.time.Instant;
import java.util.*;

/**
 * Service for collecting JVM metrics via JMX.
 * Supports both local attach and remote JMX connections.
 */
public final class JmxService {

    private final Map<Long, JMXConnector> connections = new HashMap<>();

    public JmxService() {}

    /**
     * Gets JVM metrics for a local process by PID.
     * Requires the process to have JMX enabled.
     *
     * @param pid the process ID
     * @return JVM metrics, or empty metrics if JMX is not available
     */
    public JvmMetrics getMetrics(long pid) {
        try {
            // Try to find JMX local connector address
            String jmxUrl = findLocalJmxUrl(pid);
            if (jmxUrl == null) {
                return JvmMetrics.empty(pid);
            }

            return getMetricsFromUrl(pid, jmxUrl);
        } catch (Exception e) {
            return JvmMetrics.empty(pid);
        }
    }

    /**
     * Gets JVM metrics from a remote JMX URL.
     *
     * @param pid    the process ID (for identification)
     * @param jmxUrl the JMX service URL (e.g., "service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi")
     * @return JVM metrics
     */
    public JvmMetrics getMetricsFromUrl(long pid, String jmxUrl) {
        try {
            JMXServiceURL url = new JMXServiceURL(jmxUrl);
            JMXConnector connector = connections.computeIfAbsent(pid, k -> {
                try {
                    return JMXConnectorFactory.connect(url);
                } catch (Exception e) {
                    return null;
                }
            });

            if (connector == null) {
                return JvmMetrics.empty(pid);
            }

            MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            return collectMetrics(pid, mbsc);
        } catch (Exception e) {
            connections.remove(pid);
            return JvmMetrics.empty(pid);
        }
    }

    /**
     * Closes connection for a specific PID.
     */
    public void closeConnection(long pid) {
        JMXConnector connector = connections.remove(pid);
        if (connector != null) {
            try {
                connector.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Closes all connections.
     */
    public void closeAll() {
        for (JMXConnector connector : connections.values()) {
            try {
                connector.close();
            } catch (Exception ignored) {}
        }
        connections.clear();
    }

    /**
     * Checks if a process has JMX enabled.
     */
    public boolean isJmxAvailable(long pid) {
        return findLocalJmxUrl(pid) != null;
    }

    private JvmMetrics collectMetrics(long pid, MBeanServerConnection mbsc) throws Exception {
        return new JvmMetrics(
            pid,
            collectHeapMetrics(mbsc),
            collectNonHeapMetrics(mbsc),
            collectThreadMetrics(mbsc),
            collectGcMetrics(mbsc),
            collectClassLoadingMetrics(mbsc),
            collectRuntimeInfo(mbsc),
            Instant.now()
        );
    }

    private HeapMetrics collectHeapMetrics(MBeanServerConnection mbsc) {
        try {
            ObjectName memoryMBean = new ObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
            CompositeData heapUsage = (CompositeData) mbsc.getAttribute(memoryMBean, "HeapMemoryUsage");

            long used = (Long) heapUsage.get("used");
            long committed = (Long) heapUsage.get("committed");
            long max = (Long) heapUsage.get("max");
            double usedPercent = max > 0 ? (double) used / max * 100 : 0;

            return new HeapMetrics(used, committed, max, usedPercent);
        } catch (Exception e) {
            return HeapMetrics.empty();
        }
    }

    private NonHeapMetrics collectNonHeapMetrics(MBeanServerConnection mbsc) {
        try {
            ObjectName memoryMBean = new ObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
            CompositeData nonHeapUsage = (CompositeData) mbsc.getAttribute(memoryMBean, "NonHeapMemoryUsage");

            long used = (Long) nonHeapUsage.get("used");
            long committed = (Long) nonHeapUsage.get("committed");

            return new NonHeapMetrics(used, committed);
        } catch (Exception e) {
            return NonHeapMetrics.empty();
        }
    }

    private ThreadMetrics collectThreadMetrics(MBeanServerConnection mbsc) {
        try {
            ObjectName threadMBean = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);

            int liveThreads = (Integer) mbsc.getAttribute(threadMBean, "ThreadCount");
            int daemonThreads = (Integer) mbsc.getAttribute(threadMBean, "DaemonThreadCount");
            int peakThreads = (Integer) mbsc.getAttribute(threadMBean, "PeakThreadCount");
            long totalStarted = (Long) mbsc.getAttribute(threadMBean, "TotalStartedThreadCount");

            // Check for deadlocks
            int deadlockedCount = 0;
            try {
                long[] deadlocked = (long[]) mbsc.invoke(threadMBean, "findDeadlockedThreads", null, null);
                if (deadlocked != null) {
                    deadlockedCount = deadlocked.length;
                }
            } catch (Exception ignored) {}

            return new ThreadMetrics(liveThreads, daemonThreads, peakThreads, totalStarted, deadlockedCount);
        } catch (Exception e) {
            return ThreadMetrics.empty();
        }
    }

    private GcMetrics collectGcMetrics(MBeanServerConnection mbsc) {
        try {
            Set<ObjectName> gcMBeans = mbsc.queryNames(
                new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*"), null);

            List<GcMetrics.GcCollector> collectors = new ArrayList<>();
            long totalCollections = 0;
            long totalTimeMs = 0;

            for (ObjectName gcMBean : gcMBeans) {
                String name = (String) mbsc.getAttribute(gcMBean, "Name");
                long count = (Long) mbsc.getAttribute(gcMBean, "CollectionCount");
                long time = (Long) mbsc.getAttribute(gcMBean, "CollectionTime");

                collectors.add(new GcMetrics.GcCollector(name, count, time));
                totalCollections += count;
                totalTimeMs += time;
            }

            return new GcMetrics(collectors, totalCollections, totalTimeMs);
        } catch (Exception e) {
            return GcMetrics.empty();
        }
    }

    private ClassLoadingMetrics collectClassLoadingMetrics(MBeanServerConnection mbsc) {
        try {
            ObjectName clMBean = new ObjectName(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);

            int loaded = (Integer) mbsc.getAttribute(clMBean, "LoadedClassCount");
            long totalLoaded = (Long) mbsc.getAttribute(clMBean, "TotalLoadedClassCount");
            long unloaded = (Long) mbsc.getAttribute(clMBean, "UnloadedClassCount");

            return new ClassLoadingMetrics(loaded, totalLoaded, unloaded);
        } catch (Exception e) {
            return ClassLoadingMetrics.empty();
        }
    }

    private RuntimeInfo collectRuntimeInfo(MBeanServerConnection mbsc) {
        try {
            ObjectName runtimeMBean = new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);

            String vmName = (String) mbsc.getAttribute(runtimeMBean, "VmName");
            String vmVersion = (String) mbsc.getAttribute(runtimeMBean, "VmVersion");
            String vmVendor = (String) mbsc.getAttribute(runtimeMBean, "VmVendor");
            long uptime = (Long) mbsc.getAttribute(runtimeMBean, "Uptime");

            @SuppressWarnings("unchecked")
            List<String> inputArgs = (List<String>) mbsc.getAttribute(runtimeMBean, "InputArguments");

            return new RuntimeInfo(vmName, vmVersion, vmVendor, uptime, inputArgs != null ? inputArgs : List.of());
        } catch (Exception e) {
            return RuntimeInfo.empty();
        }
    }

    private String findLocalJmxUrl(long pid) {
        // Try to get JMX URL from jcmd
        try {
            Process process = new ProcessBuilder("jcmd", String.valueOf(pid), "ManagementAgent.status")
                .redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("jmxremote.localConnectorAddress")) {
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            return line.substring(eq + 1).trim();
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            // jcmd not available or process not accessible
        }

        // Try common JMX ports
        String[] commonPorts = {"9999", "1099", "1098"};
        for (String port : commonPorts) {
            String url = "service:jmx:rmi:///jndi/rmi://localhost:" + port + "/jmxrmi";
            if (canConnect(url)) {
                return url;
            }
        }

        return null;
    }

    private boolean canConnect(String jmxUrl) {
        try {
            JMXServiceURL url = new JMXServiceURL(jmxUrl);
            JMXConnector connector = JMXConnectorFactory.connect(url);
            connector.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
