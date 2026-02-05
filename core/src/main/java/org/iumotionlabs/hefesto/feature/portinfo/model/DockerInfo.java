package org.iumotionlabs.hefesto.feature.portinfo.model;

import java.util.List;

/**
 * Record representing Docker container information.
 */
public record DockerInfo(
    String containerId,
    String containerName,
    String image,
    String status,
    List<PortMapping> portMappings
) {
    /**
     * Represents a Docker port mapping.
     */
    public record PortMapping(
        int hostPort,
        int containerPort,
        String protocol,
        String hostIp
    ) {
        /**
         * Returns formatted string: hostIp:hostPort->containerPort/protocol
         */
        public String toDisplayString() {
            String host = hostIp.isEmpty() || "0.0.0.0".equals(hostIp) ? "" : hostIp + ":";
            return "%s%d->%d/%s".formatted(host, hostPort, containerPort, protocol.toLowerCase());
        }
    }

    /**
     * Returns short container ID (first 12 characters).
     */
    public String shortId() {
        if (containerId == null || containerId.length() <= 12) {
            return containerId;
        }
        return containerId.substring(0, 12);
    }

    /**
     * Checks if container is running.
     */
    public boolean isRunning() {
        return status != null && status.toLowerCase().contains("up");
    }

    /**
     * Returns all port mappings as a formatted string.
     */
    public String portMappingsFormatted() {
        if (portMappings == null || portMappings.isEmpty()) {
            return "";
        }
        return portMappings.stream()
            .map(PortMapping::toDisplayString)
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    /**
     * Creates a DockerInfo with minimal data.
     */
    public static DockerInfo simple(String containerId, String containerName, String image, String status) {
        return new DockerInfo(containerId, containerName, image, status, List.of());
    }
}
