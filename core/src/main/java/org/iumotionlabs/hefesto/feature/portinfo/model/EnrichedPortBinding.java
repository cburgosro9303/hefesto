package org.iumotionlabs.hefesto.feature.portinfo.model;

import java.util.Optional;

/**
 * Record representing a port binding enriched with additional context.
 */
public record EnrichedPortBinding(
    PortBinding binding,
    ServiceInfo serviceInfo,
    ProcessInfo processInfo,
    DockerInfo dockerInfo
) {
    /**
     * Creates an enriched binding from a basic binding.
     */
    public static EnrichedPortBinding from(PortBinding binding) {
        return new EnrichedPortBinding(binding, null, null, null);
    }

    /**
     * Creates an enriched binding with service info.
     */
    public static EnrichedPortBinding withService(PortBinding binding, ServiceInfo serviceInfo) {
        return new EnrichedPortBinding(binding, serviceInfo, null, null);
    }

    /**
     * Returns a copy with service info.
     */
    public EnrichedPortBinding withServiceInfo(ServiceInfo info) {
        return new EnrichedPortBinding(binding, info, processInfo, dockerInfo);
    }

    /**
     * Returns a copy with process info.
     */
    public EnrichedPortBinding withProcessInfo(ProcessInfo info) {
        return new EnrichedPortBinding(binding, serviceInfo, info, dockerInfo);
    }

    /**
     * Returns a copy with Docker info.
     */
    public EnrichedPortBinding withDockerInfo(DockerInfo info) {
        return new EnrichedPortBinding(binding, serviceInfo, processInfo, info);
    }

    /**
     * Returns optional service info.
     */
    public Optional<ServiceInfo> serviceInfoOpt() {
        return Optional.ofNullable(serviceInfo);
    }

    /**
     * Returns optional process info.
     */
    public Optional<ProcessInfo> processInfoOpt() {
        return Optional.ofNullable(processInfo);
    }

    /**
     * Returns optional Docker info.
     */
    public Optional<DockerInfo> dockerInfoOpt() {
        return Optional.ofNullable(dockerInfo);
    }

    // Delegate methods to binding
    public int port() {
        return binding.port();
    }

    public String protocol() {
        return binding.protocol();
    }

    public String state() {
        return binding.state();
    }

    public long pid() {
        return binding.pid();
    }

    public String processName() {
        return binding.processName();
    }

    public String localAddress() {
        return binding.localAddress();
    }

    public String user() {
        return binding.user();
    }

    /**
     * Checks if the port is exposed to the network (0.0.0.0 or ::).
     */
    public boolean isExposed() {
        String addr = binding.localAddress();
        return "0.0.0.0".equals(addr) || "::".equals(addr) || "*".equals(addr);
    }

    /**
     * Checks if bound to localhost only.
     */
    public boolean isLocalOnly() {
        String addr = binding.localAddress();
        return "127.0.0.1".equals(addr) || "::1".equals(addr) || "localhost".equals(addr);
    }

    /**
     * Checks if this is a Docker container.
     */
    public boolean isDocker() {
        return dockerInfo != null;
    }

    /**
     * Returns a formatted text representation.
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(binding.toText());

        if (serviceInfo != null) {
            sb.append(" ").append(serviceInfo.toTag());
        }

        return sb.toString();
    }

    /**
     * Returns a compact representation with service tag.
     */
    public String toCompactWithService() {
        String base = binding.toCompact();
        if (serviceInfo != null) {
            return base + " " + serviceInfo.toTag();
        }
        return base;
    }
}
