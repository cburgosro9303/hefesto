package org.iumotionlabs.hefesto.feature.portinfo.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Record representing a complete network overview with statistics.
 */
public record NetworkOverview(
    List<EnrichedPortBinding> bindings,
    Statistics statistics,
    LocalDateTime generatedAt
) {
    /**
     * Network statistics.
     */
    public record Statistics(
        int totalListening,
        int totalEstablished,
        int tcpCount,
        int udpCount,
        int exposedCount,
        int localOnlyCount
    ) {
        /**
         * Creates statistics from a list of bindings.
         */
        public static Statistics from(List<EnrichedPortBinding> bindings) {
            int listening = 0;
            int established = 0;
            int tcp = 0;
            int udp = 0;
            int exposed = 0;
            int localOnly = 0;

            for (EnrichedPortBinding b : bindings) {
                if ("LISTEN".equals(b.state())) {
                    listening++;
                } else if ("ESTABLISHED".equals(b.state()) || "ESTAB".equals(b.state())) {
                    established++;
                }

                if ("TCP".equals(b.protocol())) {
                    tcp++;
                } else if ("UDP".equals(b.protocol())) {
                    udp++;
                }

                if (b.isExposed()) {
                    exposed++;
                } else if (b.isLocalOnly()) {
                    localOnly++;
                }
            }

            return new Statistics(listening, established, tcp, udp, exposed, localOnly);
        }

        /**
         * Returns a formatted summary line.
         */
        public String toSummaryLine() {
            return "Listening: %d | Established: %d | TCP: %d | UDP: %d | Exposed: %d | Local: %d".formatted(
                totalListening, totalEstablished, tcpCount, udpCount, exposedCount, localOnlyCount
            );
        }
    }

    /**
     * Creates a NetworkOverview from a list of enriched bindings.
     */
    public static NetworkOverview from(List<EnrichedPortBinding> bindings) {
        return new NetworkOverview(bindings, Statistics.from(bindings), LocalDateTime.now());
    }

    /**
     * Returns only listening ports.
     */
    public List<EnrichedPortBinding> listeningPorts() {
        return bindings.stream()
            .filter(b -> "LISTEN".equals(b.state()))
            .toList();
    }

    /**
     * Returns only exposed ports (bound to 0.0.0.0 or ::).
     */
    public List<EnrichedPortBinding> exposedPorts() {
        return bindings.stream()
            .filter(EnrichedPortBinding::isExposed)
            .toList();
    }

    /**
     * Returns only local-only ports (bound to 127.0.0.1 or ::1).
     */
    public List<EnrichedPortBinding> localOnlyPorts() {
        return bindings.stream()
            .filter(EnrichedPortBinding::isLocalOnly)
            .toList();
    }

    /**
     * Returns bindings grouped by process name.
     */
    public Map<String, List<EnrichedPortBinding>> byProcessName() {
        return bindings.stream()
            .collect(Collectors.groupingBy(EnrichedPortBinding::processName));
    }

    /**
     * Returns bindings grouped by PID.
     */
    public Map<Long, List<EnrichedPortBinding>> byPid() {
        return bindings.stream()
            .collect(Collectors.groupingBy(EnrichedPortBinding::pid));
    }

    /**
     * Returns bindings grouped by service category.
     */
    public Map<ServiceInfo.ServiceCategory, List<EnrichedPortBinding>> byServiceCategory() {
        return bindings.stream()
            .filter(b -> b.serviceInfo() != null)
            .collect(Collectors.groupingBy(b -> b.serviceInfo().category()));
    }

    /**
     * Returns unique process names.
     */
    public List<String> uniqueProcessNames() {
        return bindings.stream()
            .map(EnrichedPortBinding::processName)
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * Returns unique PIDs.
     */
    public List<Long> uniquePids() {
        return bindings.stream()
            .map(EnrichedPortBinding::pid)
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * Filters bindings by process name (case-insensitive partial match).
     */
    public List<EnrichedPortBinding> filterByProcessName(String name) {
        String lowerName = name.toLowerCase();
        return bindings.stream()
            .filter(b -> b.processName().toLowerCase().contains(lowerName))
            .toList();
    }

    /**
     * Returns count of Docker containers.
     */
    public long dockerContainerCount() {
        return bindings.stream()
            .filter(EnrichedPortBinding::isDocker)
            .count();
    }
}
