package org.iumotionlabs.hefesto.feature.portinfo.service;

import org.iumotionlabs.hefesto.feature.portinfo.model.DockerInfo;
import org.iumotionlabs.hefesto.feature.portinfo.model.DockerInfo.PortMapping;
import org.iumotionlabs.hefesto.feature.portinfo.model.PortBinding;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for Docker container integration and port mapping detection.
 */
public final class DockerService {

    // Pattern for docker port output: 0.0.0.0:8080->80/tcp
    private static final Pattern PORT_MAPPING_PATTERN = Pattern.compile(
        "(?:([\\d.]+|::)?:)?(\\d+)->(\\d+)/(tcp|udp)"
    );

    private Boolean dockerAvailable;

    /**
     * Checks if Docker is available on the system.
     */
    public boolean isDockerAvailable() {
        if (dockerAvailable == null) {
            dockerAvailable = checkDockerAvailable();
        }
        return dockerAvailable;
    }

    private boolean checkDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info")
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets Docker info for a container by PID.
     */
    public Optional<DockerInfo> getContainerByPid(long pid) {
        if (!isDockerAvailable()) {
            return Optional.empty();
        }

        try {
            // First, find container ID by PID using docker top or cgroup
            Optional<String> containerId = findContainerIdByPid(pid);
            if (containerId.isEmpty()) {
                return Optional.empty();
            }

            return getContainerInfo(containerId.get());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Gets Docker info for a specific container by ID or name.
     */
    public Optional<DockerInfo> getContainerInfo(String containerIdOrName) {
        if (!isDockerAvailable()) {
            return Optional.empty();
        }

        try {
            Process process = new ProcessBuilder(
                "docker", "inspect", "--format",
                "{{.Id}}|{{.Name}}|{{.Config.Image}}|{{.State.Status}}",
                containerIdOrName
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.contains("Error")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 4) {
                        String containerId = parts[0];
                        String name = parts[1].startsWith("/") ? parts[1].substring(1) : parts[1];
                        String image = parts[2];
                        String status = parts[3];

                        List<PortMapping> portMappings = getPortMappings(containerIdOrName);

                        return Optional.of(new DockerInfo(containerId, name, image, status, portMappings));
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Ignore
        }

        return Optional.empty();
    }

    /**
     * Gets all running containers with their port bindings.
     */
    public List<DockerInfo> listRunningContainers() {
        if (!isDockerAvailable()) {
            return List.of();
        }

        List<DockerInfo> containers = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(
                "docker", "ps", "--format", "{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 4) {
                        String containerId = parts[0];
                        String name = parts[1];
                        String image = parts[2];
                        String status = parts[3];

                        List<PortMapping> portMappings = getPortMappings(containerId);
                        containers.add(new DockerInfo(containerId, name, image, status, portMappings));
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Ignore
        }

        return containers;
    }

    /**
     * Gets port mappings for a container.
     */
    public List<PortMapping> getPortMappings(String containerIdOrName) {
        List<PortMapping> mappings = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(
                "docker", "port", containerIdOrName
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Format: 80/tcp -> 0.0.0.0:8080
                    parsePortLine(line).ifPresent(mappings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Ignore
        }

        return mappings;
    }

    private Optional<PortMapping> parsePortLine(String line) {
        // Format: 80/tcp -> 0.0.0.0:8080 or 80/tcp -> [::]:8080
        Pattern pattern = Pattern.compile("(\\d+)/(tcp|udp)\\s*->\\s*([\\d.:\\[\\]]+):(\\d+)");
        Matcher m = pattern.matcher(line);

        if (m.find()) {
            int containerPort = Integer.parseInt(m.group(1));
            String protocol = m.group(2);
            String hostIp = m.group(3).replace("[", "").replace("]", "");
            int hostPort = Integer.parseInt(m.group(4));

            return Optional.of(new PortMapping(hostPort, containerPort, protocol, hostIp));
        }

        return Optional.empty();
    }

    /**
     * Finds container ID by process PID.
     */
    private Optional<String> findContainerIdByPid(long pid) {
        // Method 1: Check cgroup (Linux)
        Optional<String> fromCgroup = findContainerIdFromCgroup(pid);
        if (fromCgroup.isPresent()) {
            return fromCgroup;
        }

        // Method 2: Check all containers' top
        return findContainerIdFromDockerTop(pid);
    }

    private Optional<String> findContainerIdFromCgroup(long pid) {
        try {
            Process process = new ProcessBuilder("cat", "/proc/" + pid + "/cgroup")
                .redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Look for docker container ID in cgroup path
                    Pattern pattern = Pattern.compile("docker[/-]([a-f0-9]{64})");
                    Matcher m = pattern.matcher(line);
                    if (m.find()) {
                        return Optional.of(m.group(1));
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Might not be Linux or no access
        }

        return Optional.empty();
    }

    private Optional<String> findContainerIdFromDockerTop(long pid) {
        try {
            // Get list of running containers
            Process process = new ProcessBuilder("docker", "ps", "-q")
                .redirectErrorStream(true).start();

            List<String> containerIds = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    containerIds.add(line.trim());
                }
            }
            process.waitFor();

            // Check each container's processes
            for (String containerId : containerIds) {
                if (containerHasPid(containerId, pid)) {
                    return Optional.of(containerId);
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return Optional.empty();
    }

    private boolean containerHasPid(String containerId, long pid) {
        try {
            Process process = new ProcessBuilder("docker", "top", containerId, "-o", "pid")
                .redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        if (Long.parseLong(line.trim()) == pid) {
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        // Skip non-numeric lines (header)
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    /**
     * Checks if a port binding is from a Docker container.
     */
    public boolean isDockerBinding(PortBinding binding) {
        if (!isDockerAvailable()) {
            return false;
        }

        // Check if process name suggests Docker
        String name = binding.processName().toLowerCase();
        if (name.contains("docker") || name.contains("containerd")) {
            return true;
        }

        // Check by PID
        return findContainerIdByPid(binding.pid()).isPresent();
    }

    /**
     * Finds which container is using a specific host port.
     */
    public Optional<DockerInfo> findContainerByHostPort(int port) {
        List<DockerInfo> containers = listRunningContainers();

        for (DockerInfo container : containers) {
            for (PortMapping mapping : container.portMappings()) {
                if (mapping.hostPort() == port) {
                    return Optional.of(container);
                }
            }
        }

        return Optional.empty();
    }
}
