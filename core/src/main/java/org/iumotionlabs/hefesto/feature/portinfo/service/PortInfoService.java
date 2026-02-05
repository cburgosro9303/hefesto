package org.iumotionlabs.hefesto.feature.portinfo.service;

import org.iumotionlabs.hefesto.feature.portinfo.model.*;
import org.iumotionlabs.hefesto.feature.portinfo.parser.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for orchestrating port information queries.
 */
public final class PortInfoService {

    private final PortParser parser;
    private final OperatingSystem os;
    private final ServiceRegistry serviceRegistry;

    // Common development ports
    private static final Set<Integer> DEV_PORTS = Set.of(
        3000, 3001, 4200, 5173, 5174, 8080, 8000, 8081, 8082,
        5005, 9229, 35729, 8888, 9000, 5000, 5001
    );

    public PortInfoService() {
        this.os = OperatingSystem.current();
        this.parser = createParser();
        this.serviceRegistry = new ServiceRegistry();
    }

    // For testing
    PortInfoService(PortParser parser) {
        this.os = OperatingSystem.current();
        this.parser = parser;
        this.serviceRegistry = new ServiceRegistry();
    }

    private PortParser createParser() {
        return switch (os) {
            case LINUX -> new LinuxPortParser();
            case MACOS -> new MacOsPortParser();
            case WINDOWS -> new WindowsPortParser();
            case UNKNOWN -> throw new UnsupportedOperationException(
                "Sistema operativo no soportado: " + System.getProperty("os.name")
            );
        };
    }

    /**
     * Finds bindings for a specific port.
     */
    public List<PortBinding> findByPort(int port) {
        return parser.findByPort(port, true, false);
    }

    /**
     * Finds bindings for a specific port with protocol selection.
     */
    public List<PortBinding> findByPort(int port, boolean tcp, boolean udp) {
        return parser.findByPort(port, tcp, udp);
    }

    /**
     * Finds all ports associated with a process.
     */
    public List<PortBinding> findByPid(long pid) {
        return parser.findByPid(pid);
    }

    /**
     * Finds ports in a range.
     */
    public List<PortBinding> findInRange(int from, int to, boolean listenOnly) {
        return parser.findInRange(from, to, listenOnly);
    }

    /**
     * Finds all listening ports.
     */
    public List<PortBinding> findAllListening() {
        return parser.findAllListening();
    }

    /**
     * Finds all active port bindings.
     */
    public List<PortBinding> findAll(boolean tcp, boolean udp) {
        return parser.findAll(tcp, udp);
    }

    /**
     * Finds all active port bindings (TCP and UDP).
     */
    public List<PortBinding> findAll() {
        return parser.findAll();
    }

    /**
     * Finds ports by process name (case-insensitive partial match).
     */
    public List<PortBinding> findByProcessName(String processName) {
        return parser.findByProcessName(processName);
    }

    /**
     * Finds common development ports that are in use.
     */
    public List<PortBinding> findDevPorts() {
        List<PortBinding> devBindings = new ArrayList<>();
        List<PortBinding> allListening = findAllListening();

        for (PortBinding binding : allListening) {
            if (DEV_PORTS.contains(binding.port()) || serviceRegistry.isDevelopmentPort(binding.port())) {
                devBindings.add(binding);
            }
        }

        return devBindings;
    }

    /**
     * Checks if a port is free (not in use).
     */
    public boolean isPortFree(int port) {
        // First check with quick socket test
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Finds free ports near the given port.
     * Returns a list of available ports starting from the given port.
     */
    public List<Integer> findFreePorts(int startPort, int count) {
        List<Integer> freePorts = new ArrayList<>();
        int port = startPort;
        int maxPort = Math.min(startPort + 1000, 65535);

        while (freePorts.size() < count && port <= maxPort) {
            if (isPortFree(port)) {
                freePorts.add(port);
            }
            port++;
        }

        return freePorts;
    }

    /**
     * Finds alternative ports if the given port is in use.
     * Returns the original port if free, otherwise finds nearby alternatives.
     */
    public List<Integer> findAlternatives(int port, int count) {
        List<Integer> alternatives = new ArrayList<>();

        if (isPortFree(port)) {
            alternatives.add(port);
            count--;
        }

        // Look for alternatives near the original port
        if (count > 0) {
            alternatives.addAll(findFreePorts(port + 1, count));
        }

        return alternatives;
    }

    /**
     * Gets a network overview with all listening ports and statistics.
     */
    public NetworkOverview getNetworkOverview() {
        List<PortBinding> allBindings = findAllListening();
        List<EnrichedPortBinding> enriched = enrichBindings(allBindings);
        return NetworkOverview.from(enriched);
    }

    /**
     * Enriches a list of port bindings with service information.
     */
    public List<EnrichedPortBinding> enrichBindings(List<PortBinding> bindings) {
        List<EnrichedPortBinding> enriched = new ArrayList<>();

        for (PortBinding binding : bindings) {
            EnrichedPortBinding eb = EnrichedPortBinding.from(binding);

            // Add service info
            serviceRegistry.lookup(binding.port(), binding.protocol())
                .ifPresent(service -> {
                    enriched.add(eb.withServiceInfo(service));
                    return;
                });

            if (!enriched.contains(eb)) {
                enriched.add(eb);
            }
        }

        return enriched;
    }

    /**
     * Enriches a single port binding with service information.
     */
    public EnrichedPortBinding enrichBinding(PortBinding binding) {
        EnrichedPortBinding eb = EnrichedPortBinding.from(binding);
        return serviceRegistry.lookup(binding.port(), binding.protocol())
            .map(eb::withServiceInfo)
            .orElse(eb);
    }

    /**
     * Terminates a process.
     */
    public boolean killProcess(long pid, boolean force) {
        return parser.killProcess(pid, force);
    }

    /**
     * Returns the current operating system.
     */
    public OperatingSystem getOperatingSystem() {
        return os;
    }

    /**
     * Returns the service registry.
     */
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    /**
     * Enumeration of supported operating systems.
     */
    public enum OperatingSystem {
        LINUX, MACOS, WINDOWS, UNKNOWN;

        public static OperatingSystem current() {
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("linux")) return LINUX;
            if (osName.contains("mac")) return MACOS;
            if (osName.contains("win")) return WINDOWS;
            return UNKNOWN;
        }
    }
}
