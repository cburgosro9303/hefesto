package org.iumotionlabs.hefesto.feature.portinfo.parser;

import org.iumotionlabs.hefesto.feature.portinfo.model.PortBinding;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows implementation using netstat and tasklist commands.
 */
public final class WindowsPortParser implements PortParser {

    // netstat -ano output: TCP    0.0.0.0:8080    0.0.0.0:0    LISTENING    1234
    private static final Pattern NETSTAT_PATTERN = Pattern.compile(
        "^\\s*(TCP|UDP)\\s+" +
        "([\\d.:]+|\\[::\\]):(\\d+)\\s+" +
        "([\\d.:]+|\\[::\\]|\\*:\\*):(\\d+|\\*)\\s+" +
        "(LISTENING|ESTABLISHED|TIME_WAIT|CLOSE_WAIT|FIN_WAIT_\\d)?\\s*" +
        "(\\d+)?\\s*$"
    );

    // Cache for process names
    private final Map<Long, String> processNameCache = new HashMap<>();

    @Override
    public List<PortBinding> findByPort(int port, boolean tcp, boolean udp) {
        List<PortBinding> bindings = new ArrayList<>();
        List<PortBinding> all = getAllBindings(tcp, udp);

        for (PortBinding b : all) {
            if (b.port() == port) {
                bindings.add(b);
            }
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findByPid(long pid) {
        List<PortBinding> bindings = new ArrayList<>();
        List<PortBinding> all = getAllBindings(true, true);

        for (PortBinding b : all) {
            if (b.pid() == pid) {
                bindings.add(b);
            }
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findInRange(int from, int to, boolean listenOnly) {
        List<PortBinding> bindings = new ArrayList<>();
        List<PortBinding> all = getAllBindings(true, false);

        for (PortBinding b : all) {
            if (b.port() >= from && b.port() <= to) {
                if (!listenOnly || "LISTEN".equals(b.state())) {
                    bindings.add(b);
                }
            }
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findAllListening() {
        List<PortBinding> bindings = new ArrayList<>();
        List<PortBinding> all = getAllBindings(true, true);

        for (PortBinding b : all) {
            if ("LISTEN".equals(b.state())) {
                bindings.add(b);
            }
        }

        return bindings;
    }

    @Override
    public List<PortBinding> findAll(boolean tcp, boolean udp) {
        return getAllBindings(tcp, udp);
    }

    @Override
    public List<PortBinding> findByProcessName(String processName) {
        List<PortBinding> bindings = new ArrayList<>();
        String lowerName = processName.toLowerCase();
        List<PortBinding> all = getAllBindings(true, true);

        for (PortBinding b : all) {
            if (b.processName().toLowerCase().contains(lowerName)) {
                bindings.add(b);
            }
        }

        return bindings;
    }

    @Override
    public boolean killProcess(long pid, boolean force) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("taskkill");
            cmd.add("/PID");
            cmd.add(String.valueOf(pid));
            if (force) {
                cmd.add("/F");
            }

            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private List<PortBinding> getAllBindings(boolean tcp, boolean udp) {
        List<PortBinding> bindings = new ArrayList<>();
        processNameCache.clear();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("netstat");
            cmd.add("-ano");
            if (tcp && !udp) {
                cmd.add("-p");
                cmd.add("tcp");
            } else if (udp && !tcp) {
                cmd.add("-p");
                cmd.add("udp");
            }

            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseNetstatLine(line).ifPresent(bindings::add);
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Command failed
        }

        return bindings;
    }

    private Optional<PortBinding> parseNetstatLine(String line) {
        Matcher m = NETSTAT_PATTERN.matcher(line);
        if (m.find()) {
            String protocol = m.group(1);
            String localAddr = normalizeAddress(m.group(2));
            int port;
            try {
                port = Integer.parseInt(m.group(3));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }

            String remoteAddr = normalizeAddress(m.group(4));
            String remotePortStr = m.group(5);
            int remotePort = "*".equals(remotePortStr) ? 0 : Integer.parseInt(remotePortStr);

            String state = m.group(6) != null ? m.group(6) : "";
            // Normalize state name
            if ("LISTENING".equals(state)) {
                state = "LISTEN";
            }

            String pidStr = m.group(7);
            if (pidStr == null || pidStr.isEmpty()) {
                return Optional.empty();
            }
            long pid = Long.parseLong(pidStr);

            String processName = getProcessName(pid);
            String commandLine = getCommandLine(pid);

            return Optional.of(new PortBinding(
                port, protocol, state, pid, processName, commandLine, "", localAddr, remoteAddr, remotePort
            ));
        }
        return Optional.empty();
    }

    private String normalizeAddress(String addr) {
        if (addr == null) return "0.0.0.0";
        // Handle IPv6 notation
        if (addr.startsWith("[::") || "[::]".equals(addr)) {
            return "::";
        }
        if ("*".equals(addr)) {
            return "0.0.0.0";
        }
        return addr;
    }

    private String getProcessName(long pid) {
        if (processNameCache.containsKey(pid)) {
            return processNameCache.get(pid);
        }

        try {
            Process process = new ProcessBuilder(
                "tasklist", "/fo", "csv", "/fi", "PID eq " + pid
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // Skip header
                reader.readLine();
                String line = reader.readLine();
                if (line != null) {
                    // CSV format: "Image Name","PID","Session Name","Session#","Mem Usage"
                    String[] parts = line.split(",");
                    if (parts.length > 0) {
                        String name = parts[0].replace("\"", "").trim();
                        processNameCache.put(pid, name);
                        return name;
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // Ignore
        }

        processNameCache.put(pid, "");
        return "";
    }

    private String getCommandLine(long pid) {
        try {
            Process process = new ProcessBuilder(
                "wmic", "process", "where", "ProcessId=" + pid, "get", "CommandLine", "/value"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("CommandLine=")) {
                        return line.substring("CommandLine=".length()).trim();
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            // wmic might not be available
        }

        return "";
    }
}
