package org.iumotionlabs.hefesto.feature.portinfo.output;

import org.iumotionlabs.hefesto.core.config.HefestoConfig;
import org.iumotionlabs.hefesto.core.port.output.OutputPort;
import org.iumotionlabs.hefesto.feature.portinfo.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Formats output as ASCII tables.
 */
public final class TableFormatter implements OutputFormatter {

    private final HefestoConfig config = HefestoConfig.get();
    private final boolean useColors;

    public TableFormatter() {
        this.useColors = true;
    }

    public TableFormatter(boolean useColors) {
        this.useColors = useColors;
    }

    @Override
    public String format(List<EnrichedPortBinding> bindings) {
        if (bindings.isEmpty()) {
            return "No port bindings found.";
        }

        // Calculate column widths (capped at configured max)
        int maxProcessWidth = config.maxProcessNameWidth();
        int maxServiceWidth = config.maxServiceWidth();

        int protoWidth = 5;
        int portWidth = 6;
        int stateWidth = 12;
        int addressWidth = 15;
        int pidWidth = 8;
        int processWidth = 15;
        int serviceWidth = 15;

        for (EnrichedPortBinding b : bindings) {
            int procLen = b.processName() != null ? b.processName().length() : 0;
            processWidth = Math.max(processWidth, maxProcessWidth > 0 ? Math.min(procLen, maxProcessWidth) : procLen);
            if (b.serviceInfo() != null) {
                int svcLen = b.serviceInfo().name().length();
                serviceWidth = Math.max(serviceWidth, maxServiceWidth > 0 ? Math.min(svcLen, maxServiceWidth) : svcLen);
            }
        }

        // Build header
        StringBuilder sb = new StringBuilder();
        String format = "%-" + protoWidth + "s  %-" + portWidth + "s  %-" + stateWidth + "s  %-" + addressWidth +
                       "s  %-" + pidWidth + "s  %-" + processWidth + "s  %-" + serviceWidth + "s%n";

        String header = String.format(format, "PROTO", "PORT", "STATE", "ADDRESS", "PID", "PROCESS", "SERVICE");
        String separator = "-".repeat(header.length() - 1);

        if (useColors) {
            sb.append(OutputPort.BOLD).append(header).append(OutputPort.RESET);
        } else {
            sb.append(header);
        }
        sb.append(separator).append("\n");

        // Build rows
        for (EnrichedPortBinding b : bindings) {
            String proto = b.protocol();
            String port = String.valueOf(b.port());
            String state = b.state();
            String address = b.localAddress();
            String pid = String.valueOf(b.pid());
            String process = config.truncateProcessName(b.processName());
            String service = b.serviceInfo() != null ? config.truncateService(b.serviceInfo().name()) : "-";

            if (useColors) {
                proto = colorize(proto, OutputPort.CYAN);
                state = colorizeState(state);
                process = colorize(process, OutputPort.GREEN);
                if (b.isExposed()) {
                    address = colorize(address, OutputPort.YELLOW);
                }
            }

            sb.append(String.format(format, proto, port, state, address, pid, process, service));
        }

        return sb.toString();
    }

    @Override
    public String formatOverview(NetworkOverview overview) {
        StringBuilder sb = new StringBuilder();

        // Title
        if (useColors) {
            sb.append(OutputPort.BOLD).append(OutputPort.UNDERLINE);
        }
        sb.append("NETWORK OVERVIEW\n");
        if (useColors) {
            sb.append(OutputPort.RESET);
        }
        sb.append("================\n\n");

        // Statistics
        sb.append("STATISTICS\n");
        NetworkOverview.Statistics stats = overview.statistics();
        sb.append("  Listening:     ").append(stats.totalListening()).append(" ports\n");
        sb.append("  Established:   ").append(stats.totalEstablished()).append(" connections\n");
        sb.append("  TCP: ").append(stats.tcpCount()).append(" | UDP: ").append(stats.udpCount()).append("\n");
        sb.append("  Exposed (0.0.0.0): ").append(stats.exposedCount());
        sb.append(" | Local (127.0.0.1): ").append(stats.localOnlyCount()).append("\n\n");

        // Exposed ports
        List<EnrichedPortBinding> exposed = overview.exposedPorts();
        if (!exposed.isEmpty()) {
            sb.append("EXPOSED PORTS (Network Accessible)\n");
            for (EnrichedPortBinding b : exposed) {
                String serviceTag = b.serviceInfo() != null ? b.serviceInfo().toTag() : "";
                String line = "  %s :%d   %-12s %-15s pid=%d%n".formatted(
                    b.protocol(), b.port(), b.processName(), serviceTag, b.pid()
                );
                if (useColors) {
                    sb.append(OutputPort.YELLOW);
                }
                sb.append(line);
                if (useColors) {
                    sb.append(OutputPort.RESET);
                }
            }
            sb.append("\n");
        }

        // Group by process
        Map<Long, List<EnrichedPortBinding>> byPid = overview.byPid();
        if (!byPid.isEmpty()) {
            sb.append("PROCESSES\n");
            for (Map.Entry<Long, List<EnrichedPortBinding>> entry : byPid.entrySet()) {
                List<EnrichedPortBinding> ports = entry.getValue();
                EnrichedPortBinding first = ports.get(0);

                if (useColors) {
                    sb.append(OutputPort.GREEN);
                }
                sb.append("  ").append(first.processName());
                sb.append(" (pid ").append(first.pid()).append(")");
                if (!first.user().isEmpty()) {
                    sb.append(" - ").append(first.user());
                }
                if (useColors) {
                    sb.append(OutputPort.RESET);
                }
                sb.append("\n");

                for (EnrichedPortBinding b : ports) {
                    String serviceTag = b.serviceInfo() != null ? b.serviceInfo().toTag() : "";
                    String exposure = b.isExposed() ? "0.0.0.0" : b.isLocalOnly() ? "127.0.0.1" : b.localAddress();
                    sb.append("    :").append(b.port());
                    sb.append("  ").append(b.state());
                    sb.append("  ").append(exposure);
                    sb.append("  ").append(serviceTag);
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    @Override
    public String formatSecurityReport(SecurityReport report) {
        StringBuilder sb = new StringBuilder();

        // Title
        if (useColors) {
            sb.append(OutputPort.BOLD).append(OutputPort.UNDERLINE);
        }
        sb.append("SECURITY ANALYSIS\n");
        if (useColors) {
            sb.append(OutputPort.RESET);
        }
        sb.append("=================\n\n");

        if (report.isClean()) {
            if (useColors) {
                sb.append(OutputPort.GREEN);
            }
            sb.append("No security issues found.\n");
            if (useColors) {
                sb.append(OutputPort.RESET);
            }
            return sb.toString();
        }

        // Group by severity
        Map<SecurityFlag.Severity, List<SecurityFlag>> bySeverity = report.bySeverity();

        for (SecurityFlag.Severity severity : List.of(
            SecurityFlag.Severity.CRITICAL,
            SecurityFlag.Severity.HIGH,
            SecurityFlag.Severity.WARNING,
            SecurityFlag.Severity.INFO
        )) {
            List<SecurityFlag> flags = bySeverity.getOrDefault(severity, List.of());
            if (flags.isEmpty()) continue;

            String color = switch (severity) {
                case CRITICAL -> OutputPort.RED;
                case HIGH -> OutputPort.RED;
                case WARNING -> OutputPort.YELLOW;
                case INFO -> OutputPort.CYAN;
            };

            if (useColors) {
                sb.append(color).append(OutputPort.BOLD);
            }
            sb.append(severity.displayName().toUpperCase()).append(" (").append(flags.size()).append(")\n");
            if (useColors) {
                sb.append(OutputPort.RESET);
            }

            for (SecurityFlag flag : flags) {
                if (useColors) {
                    sb.append(color);
                }
                sb.append("  ").append(flag.toSummaryLine()).append("\n");
                if (useColors) {
                    sb.append(OutputPort.RESET);
                }
            }
            sb.append("\n");
        }

        // Summary
        sb.append("SUMMARY: ").append(report.summary()).append("\n");

        return sb.toString();
    }

    @Override
    public String formatHealthCheck(HealthCheckResult result) {
        StringBuilder sb = new StringBuilder();

        // Title
        if (useColors) {
            sb.append(OutputPort.BOLD).append(OutputPort.UNDERLINE);
        }
        sb.append("PORT ").append(result.port()).append(" HEALTH CHECK\n");
        if (useColors) {
            sb.append(OutputPort.RESET);
        }
        sb.append("======================\n");

        // TCP status
        String statusColor = result.isHealthy() ? OutputPort.GREEN : OutputPort.RED;
        if (useColors) {
            sb.append("TCP:     ").append(statusColor);
        } else {
            sb.append("TCP:     ");
        }
        sb.append(result.status().displayName().toUpperCase());
        sb.append(" (").append(result.responseTimeMs()).append("ms)");
        if (useColors) {
            sb.append(OutputPort.RESET);
        }
        sb.append("\n");

        // HTTP info if available
        result.httpInfoOpt().ifPresent(http -> {
            String httpColor = http.isSuccess() ? OutputPort.GREEN : OutputPort.YELLOW;
            if (useColors) {
                sb.append("HTTP:    ").append(httpColor);
            } else {
                sb.append("HTTP:    ");
            }
            sb.append(http.statusFormatted());
            sb.append(" (").append(http.responseTimeMs()).append("ms)");
            if (useColors) {
                sb.append(OutputPort.RESET);
            }
            sb.append("\n");

            if (http.contentType() != null) {
                sb.append("Content: ").append(http.contentType());
                sb.append(" (").append(http.contentLengthFormatted()).append(")");
                sb.append("\n");
            }
        });

        // SSL info if available
        result.sslInfoOpt().ifPresent(ssl -> {
            sb.append("\nSSL CERTIFICATE\n");
            sb.append("  Subject:  ").append(extractCN(ssl.subject())).append("\n");
            sb.append("  Issuer:   ").append(extractCN(ssl.issuer())).append("\n");
            sb.append("  Protocol: ").append(ssl.protocol()).append("\n");

            long daysLeft = ssl.daysUntilExpiry();
            String expiryColor = ssl.isExpired() ? OutputPort.RED :
                                ssl.expiresSoon() ? OutputPort.YELLOW : OutputPort.GREEN;

            if (useColors) {
                sb.append("  Expires:  ").append(expiryColor);
            } else {
                sb.append("  Expires:  ");
            }

            if (ssl.isExpired()) {
                sb.append("EXPIRED");
            } else if (daysLeft >= 0) {
                sb.append(daysLeft).append(" days");
            } else {
                sb.append("Unknown");
            }

            if (useColors) {
                sb.append(OutputPort.RESET);
            }
            sb.append("\n");
        });

        return sb.toString();
    }

    @Override
    public String mimeType() {
        return "text/plain";
    }

    @Override
    public String fileExtension() {
        return "txt";
    }

    private String colorize(String text, String color) {
        if (!useColors) return text;
        return color + text + OutputPort.RESET;
    }

    private String colorizeState(String state) {
        if (!useColors) return state;
        return switch (state) {
            case "LISTEN" -> OutputPort.GREEN + state + OutputPort.RESET;
            case "ESTABLISHED", "ESTAB" -> OutputPort.BLUE + state + OutputPort.RESET;
            case "TIME_WAIT", "CLOSE_WAIT" -> OutputPort.YELLOW + state + OutputPort.RESET;
            default -> state;
        };
    }

    private String extractCN(String dn) {
        // Extract CN from X.500 DN
        if (dn == null) return "";
        for (String part : dn.split(",")) {
            if (part.trim().toUpperCase().startsWith("CN=")) {
                return part.trim().substring(3);
            }
        }
        return dn.length() > 50 ? dn.substring(0, 47) + "..." : dn;
    }

    private String truncate(String text, int maxLength) {
        return config.truncate(text, maxLength);
    }
}
