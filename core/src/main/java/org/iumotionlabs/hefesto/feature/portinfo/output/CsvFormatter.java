package org.iumotionlabs.hefesto.feature.portinfo.output;

import org.iumotionlabs.hefesto.feature.portinfo.model.*;

import java.util.List;
import java.util.StringJoiner;

/**
 * Formats output as CSV.
 */
public final class CsvFormatter implements OutputFormatter {

    private static final String DELIMITER = ",";
    private static final String NEWLINE = "\n";

    @Override
    public String format(List<EnrichedPortBinding> bindings) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(csvRow("protocol", "port", "state", "local_address", "remote_address",
                        "remote_port", "pid", "process_name", "user", "service", "category"));

        // Data rows
        for (EnrichedPortBinding b : bindings) {
            String service = b.serviceInfo() != null ? b.serviceInfo().name() : "";
            String category = b.serviceInfo() != null ? b.serviceInfo().category().name() : "";

            sb.append(csvRow(
                b.protocol(),
                String.valueOf(b.port()),
                b.state(),
                b.localAddress(),
                b.binding().remoteAddress(),
                String.valueOf(b.binding().remotePort()),
                String.valueOf(b.pid()),
                b.processName(),
                b.user(),
                service,
                category
            ));
        }

        return sb.toString();
    }

    @Override
    public String formatOverview(NetworkOverview overview) {
        StringBuilder sb = new StringBuilder();

        // Statistics section
        sb.append("# Statistics").append(NEWLINE);
        sb.append(csvRow("metric", "value"));
        NetworkOverview.Statistics stats = overview.statistics();
        sb.append(csvRow("total_listening", String.valueOf(stats.totalListening())));
        sb.append(csvRow("total_established", String.valueOf(stats.totalEstablished())));
        sb.append(csvRow("tcp_count", String.valueOf(stats.tcpCount())));
        sb.append(csvRow("udp_count", String.valueOf(stats.udpCount())));
        sb.append(csvRow("exposed_count", String.valueOf(stats.exposedCount())));
        sb.append(csvRow("local_only_count", String.valueOf(stats.localOnlyCount())));
        sb.append(NEWLINE);

        // Port bindings section
        sb.append("# Port Bindings").append(NEWLINE);
        sb.append(format(overview.bindings()));

        return sb.toString();
    }

    @Override
    public String formatSecurityReport(SecurityReport report) {
        StringBuilder sb = new StringBuilder();

        // Summary
        sb.append("# Security Report Summary").append(NEWLINE);
        sb.append(csvRow("severity", "count"));
        sb.append(csvRow("critical", String.valueOf(report.criticalCount())));
        sb.append(csvRow("high", String.valueOf(report.highCount())));
        sb.append(csvRow("warning", String.valueOf(report.warningCount())));
        sb.append(csvRow("info", String.valueOf(report.infoCount())));
        sb.append(NEWLINE);

        // Findings
        sb.append("# Findings").append(NEWLINE);
        sb.append(csvRow("severity", "category", "title", "description", "recommendation",
                        "port", "process", "pid"));

        for (SecurityFlag flag : report.sortedBySeverity()) {
            PortBinding b = flag.relatedBinding();
            sb.append(csvRow(
                flag.severity().name(),
                flag.category().name(),
                flag.title(),
                flag.description(),
                flag.recommendation(),
                b != null ? String.valueOf(b.port()) : "",
                b != null ? b.processName() : "",
                b != null ? String.valueOf(b.pid()) : ""
            ));
        }

        return sb.toString();
    }

    @Override
    public String formatHealthCheck(HealthCheckResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append(csvRow("field", "value"));
        sb.append(csvRow("port", String.valueOf(result.port())));
        sb.append(csvRow("protocol", result.protocol()));
        sb.append(csvRow("status", result.status().name()));
        sb.append(csvRow("response_time_ms", String.valueOf(result.responseTimeMs())));
        sb.append(csvRow("healthy", String.valueOf(result.isHealthy())));

        result.httpInfoOpt().ifPresent(http -> {
            sb.append(csvRow("http_status_code", String.valueOf(http.statusCode())));
            sb.append(csvRow("http_status_text", http.statusText()));
            sb.append(csvRow("content_type", http.contentType() != null ? http.contentType() : ""));
            sb.append(csvRow("content_length", String.valueOf(http.contentLength())));
        });

        result.sslInfoOpt().ifPresent(ssl -> {
            sb.append(csvRow("ssl_issuer", ssl.issuer()));
            sb.append(csvRow("ssl_subject", ssl.subject()));
            sb.append(csvRow("ssl_protocol", ssl.protocol()));
            sb.append(csvRow("ssl_valid", String.valueOf(ssl.valid())));
            sb.append(csvRow("ssl_days_until_expiry", String.valueOf(ssl.daysUntilExpiry())));
        });

        return sb.toString();
    }

    @Override
    public String mimeType() {
        return "text/csv";
    }

    @Override
    public String fileExtension() {
        return "csv";
    }

    private String csvRow(String... values) {
        StringJoiner joiner = new StringJoiner(DELIMITER);
        for (String value : values) {
            joiner.add(escapeCsv(value));
        }
        return joiner.toString() + NEWLINE;
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If contains delimiter, newline, or quote, wrap in quotes
        if (value.contains(DELIMITER) || value.contains("\n") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
