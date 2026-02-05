package org.iumotionlabs.hefesto.feature.portinfo.parser;

import org.iumotionlabs.hefesto.feature.portinfo.model.PortBinding;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * macOS implementation using lsof command.
 */
public final class MacOsPortParser implements PortParser {

    // lsof -nP output: java      1234  user   5u  IPv4 0x...  0t0  TCP *:8080 (LISTEN)
    // Or with connection: java  1234  user   5u  IPv4 0x...  0t0  TCP 127.0.0.1:8080->127.0.0.1:54321 (ESTABLISHED)
    private static final Pattern LSOF_PATTERN = Pattern.compile(
        "^(\\S+)\\s+(\\d+)\\s+(\\S+)\\s+\\S+\\s+(IPv[46])\\s+\\S+\\s+\\S+\\s+(TCP|UDP)\\s+" +
        "([^:]+|\\*):(\\d+|\\*)(?:->([^:]+):(\\d+))?\\s*(?:\\(([^)]+)\\))?"
    );

    @Override
    public List<PortBinding> findByPort(int port, boolean tcp, boolean udp) {
        List<PortBinding> bindings = new ArrayList<>();

        if (tcp) {
            bindings.addAll(runLsof("-iTCP:" + port, "TCP"));
        }

        if (udp) {
            bindings.addAll(runLsof("-iUDP:" + port, "UDP"));
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findByPid(long pid) {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(
                "lsof", "-nP", "-p", String.valueOf(pid), "-iTCP", "-iUDP"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLsofLine(line).ifPresent(bindings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command might not be available or permission denied
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findInRange(int from, int to, boolean listenOnly) {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("lsof");
            cmd.add("-nP");
            cmd.add("-iTCP");
            if (listenOnly) {
                cmd.add("-sTCP:LISTEN");
            }

            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLsofLine(line).ifPresent(binding -> {
                        if (binding.port() >= from && binding.port() <= to) {
                            bindings.add(binding);
                        }
                    });
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command failed
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findAllListening() {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            Process process = new ProcessBuilder("lsof", "-nP", "-iTCP", "-sTCP:LISTEN")
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLsofLine(line).ifPresent(bindings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command failed
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findAll(boolean tcp, boolean udp) {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("lsof");
            cmd.add("-nP");

            if (tcp && udp) {
                cmd.add("-iTCP");
                cmd.add("-iUDP");
            } else if (tcp) {
                cmd.add("-iTCP");
            } else if (udp) {
                cmd.add("-iUDP");
            } else {
                return bindings;
            }

            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLsofLine(line).ifPresent(bindings::add);
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

        try {
            Process process = new ProcessBuilder("lsof", "-nP", "-iTCP", "-iUDP")
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLsofLine(line).ifPresent(binding -> {
                        if (binding.processName().toLowerCase().contains(lowerName)) {
                            bindings.add(binding);
                        }
                    });
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command failed
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

    private List<PortBinding> runLsof(String portArg, String protocol) {
        List<PortBinding> bindings = new ArrayList<>();

        try {
            Process process = new ProcessBuilder("lsof", "-nP", portArg)
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLsofLine(line).ifPresent(bindings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command failed
        }

        return bindings;
    }

    private Optional<PortBinding> parseLsofLine(String line) {
        Matcher m = LSOF_PATTERN.matcher(line);
        if (m.find()) {
            String processName = m.group(1);
            long pid = Long.parseLong(m.group(2));
            String user = m.group(3);
            String protocol = m.group(5);
            String localAddr = m.group(6);
            String portStr = m.group(7);
            String remoteAddr = m.group(8) != null ? m.group(8) : "";
            String remotePortStr = m.group(9);
            String state = m.group(10) != null ? m.group(10) : "";

            // Skip header lines or invalid entries
            if ("*".equals(portStr) || portStr.isEmpty()) {
                return Optional.empty();
            }

            try {
                int port = Integer.parseInt(portStr);
                int remotePort = remotePortStr != null ? Integer.parseInt(remotePortStr) : 0;

                // Normalize local address
                if ("*".equals(localAddr)) {
                    localAddr = "0.0.0.0";
                }

                // Get command line (optional, requires additional call)
                String commandLine = getCommandLine(pid);

                return Optional.of(new PortBinding(
                    port, protocol, state, pid, processName, commandLine, user, localAddr, remoteAddr, remotePort
                ));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String getCommandLine(long pid) {
        try {
            Process process = new ProcessBuilder("ps", "-p", String.valueOf(pid), "-o", "command=")
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null ? line.trim() : "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}
