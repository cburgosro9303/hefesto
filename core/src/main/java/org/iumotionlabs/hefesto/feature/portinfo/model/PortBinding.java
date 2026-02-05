package org.iumotionlabs.hefesto.feature.portinfo.model;

/**
 * Record representing a port binding with process information.
 */
public record PortBinding(
    int port,
    String protocol,      // TCP, UDP
    String state,         // LISTEN, ESTABLISHED, TIME_WAIT, etc.
    long pid,
    String processName,
    String commandLine,
    String user,
    String localAddress,
    String remoteAddress, // For established connections
    int remotePort
) {
    /**
     * Creates a minimal binding for LISTEN ports.
     */
    public static PortBinding listen(int port, String protocol, long pid, String processName) {
        return new PortBinding(port, protocol, "LISTEN", pid, processName, "", "", "0.0.0.0", "", 0);
    }

    /**
     * Returns a formatted text representation.
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol).append(" ");
        sb.append(localAddress).append(":").append(port).append(" ");
        sb.append(state).append(" ");
        sb.append("pid=").append(pid).append(" ");
        sb.append(processName);

        if (!commandLine.isEmpty()) {
            sb.append(" cmd=\"").append(truncate(commandLine, 256)).append("\"");
        }

        if (!user.isEmpty()) {
            sb.append(" user=").append(user);
        }

        if (!remoteAddress.isEmpty() && remotePort > 0) {
            sb.append(" -> ").append(remoteAddress).append(":").append(remotePort);
        }

        return sb.toString();
    }

    /**
     * Returns a compact one-line representation.
     */
    public String toCompact() {
        return "%s :%d %s %s (pid %d)".formatted(protocol, port, state, processName, pid);
    }

    private static String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) return s;
        return s.substring(0, maxLength - 3) + "...";
    }
}
