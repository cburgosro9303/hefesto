package org.iumotionlabs.hefesto.feature.portinfo.output;

import org.iumotionlabs.hefesto.feature.portinfo.model.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Formats output as JSON with enhanced structure.
 */
public final class JsonFormatter implements OutputFormatter {

    private final ObjectMapper mapper;
    private final boolean pretty;

    public JsonFormatter() {
        this(true);
    }

    public JsonFormatter(boolean pretty) {
        this.pretty = pretty;
        JsonMapper.Builder builder = JsonMapper.builder();
        if (pretty) {
            builder.enable(SerializationFeature.INDENT_OUTPUT);
        }
        this.mapper = builder.build();
    }

    @Override
    public String format(List<EnrichedPortBinding> bindings) {
        try {
            ArrayNode array = mapper.createArrayNode();

            for (EnrichedPortBinding b : bindings) {
                ObjectNode node = mapper.createObjectNode();
                node.put("port", b.port());
                node.put("protocol", b.protocol());
                node.put("state", b.state());
                node.put("localAddress", b.localAddress());
                node.put("remoteAddress", b.binding().remoteAddress());
                node.put("remotePort", b.binding().remotePort());
                node.put("pid", b.pid());
                node.put("processName", b.processName());
                node.put("user", b.user());
                node.put("commandLine", b.binding().commandLine());
                node.put("isExposed", b.isExposed());
                node.put("isLocalOnly", b.isLocalOnly());

                if (b.serviceInfo() != null) {
                    ObjectNode serviceNode = mapper.createObjectNode();
                    serviceNode.put("name", b.serviceInfo().name());
                    serviceNode.put("description", b.serviceInfo().description());
                    serviceNode.put("category", b.serviceInfo().category().name());
                    node.set("service", serviceNode);
                }

                if (b.dockerInfo() != null) {
                    ObjectNode dockerNode = mapper.createObjectNode();
                    dockerNode.put("containerId", b.dockerInfo().shortId());
                    dockerNode.put("containerName", b.dockerInfo().containerName());
                    dockerNode.put("image", b.dockerInfo().image());
                    dockerNode.put("status", b.dockerInfo().status());
                    node.set("docker", dockerNode);
                }

                array.add(node);
            }

            return mapper.writeValueAsString(array);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public String formatOverview(NetworkOverview overview) {
        try {
            ObjectNode root = mapper.createObjectNode();

            // Statistics
            ObjectNode stats = mapper.createObjectNode();
            stats.put("totalListening", overview.statistics().totalListening());
            stats.put("totalEstablished", overview.statistics().totalEstablished());
            stats.put("tcpCount", overview.statistics().tcpCount());
            stats.put("udpCount", overview.statistics().udpCount());
            stats.put("exposedCount", overview.statistics().exposedCount());
            stats.put("localOnlyCount", overview.statistics().localOnlyCount());
            root.set("statistics", stats);

            // Timestamp
            root.put("generatedAt", overview.generatedAt().toString());

            // Bindings grouped by process
            ObjectNode processes = mapper.createObjectNode();
            for (Map.Entry<Long, List<EnrichedPortBinding>> entry : overview.byPid().entrySet()) {
                List<EnrichedPortBinding> ports = entry.getValue();
                if (ports.isEmpty()) continue;

                EnrichedPortBinding first = ports.get(0);
                ObjectNode processNode = mapper.createObjectNode();
                processNode.put("pid", first.pid());
                processNode.put("name", first.processName());
                processNode.put("user", first.user());

                ArrayNode portsArray = mapper.createArrayNode();
                for (EnrichedPortBinding b : ports) {
                    ObjectNode portNode = mapper.createObjectNode();
                    portNode.put("port", b.port());
                    portNode.put("protocol", b.protocol());
                    portNode.put("state", b.state());
                    portNode.put("address", b.localAddress());
                    portNode.put("exposed", b.isExposed());
                    if (b.serviceInfo() != null) {
                        portNode.put("service", b.serviceInfo().name());
                    }
                    portsArray.add(portNode);
                }
                processNode.set("ports", portsArray);

                processes.set(String.valueOf(entry.getKey()), processNode);
            }
            root.set("processes", processes);

            // Exposed ports summary
            ArrayNode exposed = mapper.createArrayNode();
            for (EnrichedPortBinding b : overview.exposedPorts()) {
                ObjectNode node = mapper.createObjectNode();
                node.put("port", b.port());
                node.put("process", b.processName());
                node.put("pid", b.pid());
                if (b.serviceInfo() != null) {
                    node.put("service", b.serviceInfo().name());
                }
                exposed.add(node);
            }
            root.set("exposedPorts", exposed);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public String formatSecurityReport(SecurityReport report) {
        try {
            ObjectNode root = mapper.createObjectNode();

            // Summary
            ObjectNode summary = mapper.createObjectNode();
            summary.put("critical", report.criticalCount());
            summary.put("high", report.highCount());
            summary.put("warning", report.warningCount());
            summary.put("info", report.infoCount());
            summary.put("totalPortsAnalyzed", report.totalPortsAnalyzed());
            summary.put("clean", report.isClean());
            root.set("summary", summary);

            root.put("generatedAt", report.generatedAt().toString());

            // Findings
            ArrayNode findings = mapper.createArrayNode();
            for (SecurityFlag flag : report.sortedBySeverity()) {
                ObjectNode node = mapper.createObjectNode();
                node.put("severity", flag.severity().name());
                node.put("category", flag.category().name());
                node.put("title", flag.title());
                node.put("description", flag.description());
                node.put("recommendation", flag.recommendation());

                if (flag.relatedBinding() != null) {
                    ObjectNode binding = mapper.createObjectNode();
                    binding.put("port", flag.relatedBinding().port());
                    binding.put("protocol", flag.relatedBinding().protocol());
                    binding.put("process", flag.relatedBinding().processName());
                    binding.put("pid", flag.relatedBinding().pid());
                    binding.put("address", flag.relatedBinding().localAddress());
                    node.set("binding", binding);
                }

                findings.add(node);
            }
            root.set("findings", findings);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public String formatHealthCheck(HealthCheckResult result) {
        try {
            ObjectNode root = mapper.createObjectNode();

            root.put("port", result.port());
            root.put("protocol", result.protocol());
            root.put("status", result.status().name());
            root.put("statusDisplay", result.status().displayName());
            root.put("healthy", result.isHealthy());
            root.put("responseTimeMs", result.responseTimeMs());
            root.put("message", result.message());
            root.put("timestamp", result.timestamp().toString());

            result.httpInfoOpt().ifPresent(http -> {
                ObjectNode httpNode = mapper.createObjectNode();
                httpNode.put("statusCode", http.statusCode());
                httpNode.put("statusText", http.statusText());
                httpNode.put("contentType", http.contentType());
                httpNode.put("contentLength", http.contentLength());
                httpNode.put("responseTimeMs", http.responseTimeMs());
                httpNode.put("success", http.isSuccess());
                root.set("http", httpNode);
            });

            result.sslInfoOpt().ifPresent(ssl -> {
                ObjectNode sslNode = mapper.createObjectNode();
                sslNode.put("issuer", ssl.issuer());
                sslNode.put("subject", ssl.subject());
                sslNode.put("validFrom", ssl.validFrom() != null ? ssl.validFrom().toString() : null);
                sslNode.put("validTo", ssl.validTo() != null ? ssl.validTo().toString() : null);
                sslNode.put("protocol", ssl.protocol());
                sslNode.put("cipherSuite", ssl.cipherSuite());
                sslNode.put("valid", ssl.valid());
                sslNode.put("expired", ssl.isExpired());
                sslNode.put("expiresSoon", ssl.expiresSoon());
                sslNode.put("daysUntilExpiry", ssl.daysUntilExpiry());
                root.set("ssl", sslNode);
            });

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public String mimeType() {
        return "application/json";
    }

    @Override
    public String fileExtension() {
        return "json";
    }
}
