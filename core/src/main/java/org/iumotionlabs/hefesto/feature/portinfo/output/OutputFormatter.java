package org.iumotionlabs.hefesto.feature.portinfo.output;

import org.iumotionlabs.hefesto.feature.portinfo.model.*;

import java.util.List;

/**
 * Sealed interface for output formatting of port information.
 */
public sealed interface OutputFormatter
    permits TableFormatter, CsvFormatter, JsonFormatter {

    /**
     * Formats a list of enriched port bindings.
     */
    String format(List<EnrichedPortBinding> bindings);

    /**
     * Formats a network overview.
     */
    String formatOverview(NetworkOverview overview);

    /**
     * Formats a security report.
     */
    String formatSecurityReport(SecurityReport report);

    /**
     * Formats a health check result.
     */
    String formatHealthCheck(HealthCheckResult result);

    /**
     * Formats a list of raw port bindings.
     */
    default String formatRaw(List<PortBinding> bindings) {
        List<EnrichedPortBinding> enriched = bindings.stream()
            .map(EnrichedPortBinding::from)
            .toList();
        return format(enriched);
    }

    /**
     * Returns the MIME type for this format.
     */
    String mimeType();

    /**
     * Returns file extension for this format.
     */
    String fileExtension();
}
