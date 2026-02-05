package org.iumotionlabs.hefesto.feature.portinfo.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Record representing a complete security analysis report.
 */
public record SecurityReport(
    List<SecurityFlag> findings,
    LocalDateTime generatedAt,
    int totalPortsAnalyzed
) {
    /**
     * Creates an empty report.
     */
    public static SecurityReport empty() {
        return new SecurityReport(List.of(), LocalDateTime.now(), 0);
    }

    /**
     * Creates a report with findings.
     */
    public static SecurityReport of(List<SecurityFlag> findings, int totalPorts) {
        return new SecurityReport(findings, LocalDateTime.now(), totalPorts);
    }

    /**
     * Returns findings grouped by severity.
     */
    public Map<SecurityFlag.Severity, List<SecurityFlag>> bySeverity() {
        return findings.stream()
            .collect(Collectors.groupingBy(SecurityFlag::severity));
    }

    /**
     * Returns findings grouped by category.
     */
    public Map<SecurityFlag.Category, List<SecurityFlag>> byCategory() {
        return findings.stream()
            .collect(Collectors.groupingBy(SecurityFlag::category));
    }

    /**
     * Returns count of critical findings.
     */
    public long criticalCount() {
        return countBySeverity(SecurityFlag.Severity.CRITICAL);
    }

    /**
     * Returns count of high severity findings.
     */
    public long highCount() {
        return countBySeverity(SecurityFlag.Severity.HIGH);
    }

    /**
     * Returns count of warning findings.
     */
    public long warningCount() {
        return countBySeverity(SecurityFlag.Severity.WARNING);
    }

    /**
     * Returns count of info findings.
     */
    public long infoCount() {
        return countBySeverity(SecurityFlag.Severity.INFO);
    }

    /**
     * Counts findings by severity.
     */
    public long countBySeverity(SecurityFlag.Severity severity) {
        return findings.stream()
            .filter(f -> f.severity() == severity)
            .count();
    }

    /**
     * Checks if there are critical or high severity issues.
     */
    public boolean hasCriticalIssues() {
        return criticalCount() > 0 || highCount() > 0;
    }

    /**
     * Checks if the report is clean (no findings).
     */
    public boolean isClean() {
        return findings.isEmpty();
    }

    /**
     * Returns a summary line.
     */
    public String summary() {
        return "%d critical, %d high, %d warnings, %d info".formatted(
            criticalCount(), highCount(), warningCount(), infoCount()
        );
    }

    /**
     * Returns findings sorted by severity (highest first).
     */
    public List<SecurityFlag> sortedBySeverity() {
        return findings.stream()
            .sorted((a, b) -> Integer.compare(b.severity().level(), a.severity().level()))
            .toList();
    }

    /**
     * Filters findings by minimum severity.
     */
    public List<SecurityFlag> filterByMinSeverity(SecurityFlag.Severity minSeverity) {
        return findings.stream()
            .filter(f -> f.severity().level() >= minSeverity.level())
            .toList();
    }
}
