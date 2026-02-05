package org.iumotionlabs.hefesto.feature.portinfo.parser;

import org.iumotionlabs.hefesto.feature.portinfo.model.PortBinding;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linux implementation using ss and lsof commands.
 */
public final class LinuxPortParser implements PortParser {

    // ss output pattern: LISTEN  0  128  0.0.0.0:8080  0.0.0.0:*  users:(("java",pid=1234,fd=5))
    private static final Pattern SS_PATTERN = Pattern.compile(
        "(LISTEN|ESTAB|TIME-WAIT|CLOSE-WAIT)\\s+\\d+\\s+\\d+\\s+" +
        "([\\d.:*]+):(\\d+)\\s+" +
        "([\\d.:*]+):(\\d+|\\*)\\s*" +
        "(?:users:\\(\\(\"([^\"]+)\",pid=(\\d+).*\\)\\))?"
    );

    // lsof output pattern: java  1234  user  5u  IPv4  12345  0t0  TCP *:8080 (LISTEN)
    private static final Pattern LSOF_PATTERN = Pattern.compile(
        "(\\S+)\\s+(\\d+)\\s+(\\S+)\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+(TCP|UDP)\\s+" +
        "([^:]+):(\\d+|\\*)(?:->([^:]+):(\\d+))?\\s*\\((\\w+)\\)?"
    );

    @Override
    public List<PortBinding> findByPort(int port, boolean tcp, boolean udp) {
        List<PortBinding> bindings = new ArrayList<>();

        if (tcp) {
            bindings.addAll(findWithSs(port, "tcp"));
            if (bindings.isEmpty()) {
                bindings.addAll(findWithLsof(port, "TCP"));
            }
        }

        if (udp) {
            bindings.addAll(findWithSs(port, "udp"));
            if (bindings.isEmpty()) {
                bindings.addAll(findWithLsof(port, "UDP"));
            }
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findByPid(long pid) {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            Process process = new ProcessBuilder("lsof", "-nP", "-p", String.valueOf(pid), "-iTCP", "-iUDP")
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLosfLine(line).ifPresent(bindings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command might not be available
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findInRange(int from, int to, boolean listenOnly) {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("ss");
            cmd.add("-tlnp");
            if (!listenOnly) {
                cmd.set(1, "-tanp");
            }

            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseSsLine(line).ifPresent(binding -> {
                        if (binding.port() >= from && binding.port() <= to) {
                            bindings.add(binding);
                        }
                    });
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Fallback to lsof
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findAllListening() {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            Process process = new ProcessBuilder("ss", "-tlnp")
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseSsLine(line).ifPresent(bindings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Fallback to lsof
            try {
                Process lsofProcess = new ProcessBuilder("lsof", "-nP", "-iTCP", "-sTCP:LISTEN")
                    .redirectErrorStream(true)
                    .start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(lsofProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        parseLosfLine(line).ifPresent(bindings::add);
                    }
                }

                lsofProcess.waitFor();
            } catch (Exception ex) {
                // Both failed
            }
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findAll(boolean tcp, boolean udp) {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            StringBuilder flags = new StringBuilder("-");
            if (tcp) flags.append("t");
            if (udp) flags.append("u");
            flags.append("anp");

            Process process = new ProcessBuilder("ss", flags.toString())
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseSsLine(line).ifPresent(bindings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command failed
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findByProcessName(String processName) {
        List<PortBinding> bindings = new ArrayList<>();
        String lowerName = processName.toLowerCase();

        // Get all bindings and filter by process name
        List<PortBinding> all = findAll(true, true);
        for (PortBinding binding : all) {
            if (binding.processName().toLowerCase().contains(lowerName)) {
                bindings.add(binding);
            }
        }

        return bindings;
    }

    @Override
    public boolean killProcess(long pid, boolean force) {
        try {
            String signal = force ? "-9" : "-15";
            Process process = new ProcessBuilder("kill", signal, String.valueOf(pid))
                .redirectErrorStream(true)
                .start();

            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private List<PortBinding> findWithSs(int port, String protocol) {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(
                "ss", "-" + protocol.charAt(0) + "lnp",
                "sport", "=", ":" + port
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseSsLine(line).ifPresent(bindings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command failed
        }

        return bindings;
    }

    private List<PortBinding> findWithLsof(int port, String protocol) {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(
                "lsof", "-nP", "-i" + protocol + ":" + port
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLosfLine(line).ifPresent(bindings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command failed
        }

        return bindings;
    }

    private java.util.Optional<PortBinding> parseSsLine(String line) {
        Matcher m = SS_PATTERN.matcher(line);
        if (m.find()) {
            String state = m.group(1);
            String localAddr = m.group(2);
            int port = Integer.parseInt(m.group(3));
            String remoteAddr = m.group(4);
            String remotePortStr = m.group(5);
            String processName = m.group(6) != null ? m.group(6) : "";
            long pid = m.group(7) != null ? Long.parseLong(m.group(7)) : 0;

            int remotePort = "*".equals(remotePortStr) ? 0 : Integer.parseInt(remotePortStr);

            return java.util.Optional.of(new PortBinding(
                port, "TCP", state, pid, processName, "", "", localAddr, remoteAddr, remotePort
            ));
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<PortBinding> parseLosfLine(String line) {
        Matcher m = LSOF_PATTERN.matcher(line);
        if (m.find()) {
            String processName = m.group(1);
            long pid = Long.parseLong(m.group(2));
            String user = m.group(3);
            String protocol = m.group(4);
            String localAddr = m.group(5);
            String portStr = m.group(6);
            String remoteAddr = m.group(7) != null ? m.group(7) : "";
            String remotePortStr = m.group(8);
            String state = m.group(9) != null ? m.group(9) : "";

            if ("*".equals(portStr)) return java.util.Optional.empty();
            int port = Integer.parseInt(portStr);
            int remotePort = remotePortStr != null ? Integer.parseInt(remotePortStr) : 0;

            return java.util.Optional.of(new PortBinding(
                port, protocol, state, pid, processName, "", user, localAddr, remoteAddr, remotePort
            ));
        }
        return java.util.Optional.empty();
    }
}
