package org.iumotionlabs.hefesto.feature.portinfo.service;

import org.iumotionlabs.hefesto.feature.portinfo.model.*;
import org.iumotionlabs.hefesto.feature.portinfo.model.SecurityFlag.Category;
import org.iumotionlabs.hefesto.feature.portinfo.model.SecurityFlag.Severity;

import java.util.*;

/**
 * Service for analyzing port bindings for security issues.
 */
public final class SecurityAnalysisService {

    private final ServiceRegistry serviceRegistry;

    // Debug ports that should never be exposed
    private static final Set<Integer> DEBUG_PORTS = Set.of(5005, 9229, 5858, 4000);

    // Database ports that are risky when exposed
    private static final Set<Integer> DATABASE_PORTS = Set.of(
        3306, 5432, 27017, 6379, 1521, 1433, 9042, 5984
    );

    // Admin/management interfaces that should be local-only
    private static final Set<Integer> ADMIN_PORTS = Set.of(
        15672, 9090, 8500, 8200, 8761, 9411, 16686
    );

    // High-risk users
    private static final Set<String> PRIVILEGED_USERS = Set.of(
        "root", "SYSTEM", "Administrator", "LocalSystem"
    );

    public SecurityAnalysisService() {
        this.serviceRegistry = new ServiceRegistry();
    }

    public SecurityAnalysisService(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * Analyzes a single port binding for security issues.
     */
    public List<SecurityFlag> analyze(PortBinding binding) {
        List<SecurityFlag> flags = new ArrayList<>();

        // Check for network exposure
        if (isExposed(binding)) {
            flags.addAll(analyzeExposedPort(binding));
        }

        // Check for privileged execution
        if (isRunningAsPrivileged(binding)) {
            flags.add(analyzePrivilegedProcess(binding));
        }

        // Check for debug ports
        if (isDebugPort(binding.port())) {
            flags.add(analyzeDebugPort(binding));
        }

        return flags;
    }

    /**
     * Analyzes multiple port bindings and generates a security report.
     */
    public SecurityReport analyze(List<PortBinding> bindings) {
        List<SecurityFlag> allFlags = new ArrayList<>();

        for (PortBinding binding : bindings) {
            allFlags.addAll(analyze(binding));
        }

        // Sort by severity
        allFlags.sort((a, b) -> Integer.compare(b.severity().level(), a.severity().level()));

        return SecurityReport.of(allFlags, bindings.size());
    }

    /**
     * Analyzes enriched port bindings.
     */
    public SecurityReport analyzeEnriched(List<EnrichedPortBinding> bindings) {
        List<PortBinding> rawBindings = bindings.stream()
            .map(EnrichedPortBinding::binding)
            .toList();
        return analyze(rawBindings);
    }

    private boolean isExposed(PortBinding binding) {
        String addr = binding.localAddress();
        return "0.0.0.0".equals(addr) || "::".equals(addr) || "*".equals(addr);
    }

    private boolean isRunningAsPrivileged(PortBinding binding) {
        return PRIVILEGED_USERS.contains(binding.user());
    }

    private boolean isDebugPort(int port) {
        return DEBUG_PORTS.contains(port);
    }

    private boolean isDatabasePort(int port) {
        return DATABASE_PORTS.contains(port);
    }

    private boolean isAdminPort(int port) {
        return ADMIN_PORTS.contains(port);
    }

    private List<SecurityFlag> analyzeExposedPort(PortBinding binding) {
        List<SecurityFlag> flags = new ArrayList<>();
        int port = binding.port();

        // Database exposed
        if (isDatabasePort(port)) {
            Optional<ServiceInfo> service = serviceRegistry.lookupTcp(port);
            String serviceName = service.map(ServiceInfo::name).orElse("Database");

            Severity severity = isRunningAsPrivileged(binding) ? Severity.CRITICAL : Severity.HIGH;

            String title = serviceName + " Exposed";
            String description = "Database port exposed to network";
            String recommendation = "Bind to 127.0.0.1 or use firewall rules to restrict access";

            if (isRunningAsPrivileged(binding)) {
                description += " and running as " + binding.user();
                recommendation += ". Also consider running as non-privileged user.";
            }

            flags.add(new SecurityFlag(severity, Category.DATABASE, title, description, recommendation, binding));
        }

        // Debug port exposed
        else if (isDebugPort(port)) {
            flags.add(SecurityFlag.critical(
                Category.DEBUG,
                "Debug Port Exposed",
                "Debug port " + port + " exposed to network - allows remote code execution",
                "Bind debug port to 127.0.0.1 only: -agentlib:jdwp=...,address=127.0.0.1:" + port,
                binding
            ));
        }

        // Admin interface exposed
        else if (isAdminPort(port)) {
            Optional<ServiceInfo> service = serviceRegistry.lookupTcp(port);
            String serviceName = service.map(ServiceInfo::name).orElse("Admin interface");

            flags.add(SecurityFlag.high(
                Category.CONFIGURATION,
                serviceName + " Exposed",
                "Administrative interface exposed to network",
                "Bind to 127.0.0.1 or use authentication and firewall rules",
                binding
            ));
        }

        // Generic network exposure warning
        else {
            Optional<ServiceInfo> service = serviceRegistry.lookupTcp(port);
            String serviceName = service.map(ServiceInfo::name).orElse("Service");

            flags.add(SecurityFlag.warning(
                Category.NETWORK_EXPOSURE,
                serviceName + " Network Exposed",
                "Port " + port + " is accessible from network (0.0.0.0)",
                "Consider binding to 127.0.0.1 if network access is not required",
                binding
            ));
        }

        return flags;
    }

    private SecurityFlag analyzePrivilegedProcess(PortBinding binding) {
        // Higher severity for user-facing ports
        Severity severity = binding.port() < 1024 ? Severity.WARNING : Severity.HIGH;

        if (isDatabasePort(binding.port())) {
            severity = Severity.HIGH;
        }

        return new SecurityFlag(
            severity,
            Category.PRIVILEGE,
            "Running as " + binding.user(),
            binding.processName() + " (port " + binding.port() + ") running with elevated privileges",
            "Run as non-privileged user. Use systemd socket activation or capabilities for low ports.",
            binding
        );
    }

    private SecurityFlag analyzeDebugPort(PortBinding binding) {
        if (isExposed(binding)) {
            return SecurityFlag.critical(
                Category.DEBUG,
                "Debug Port Active",
                "Debug port " + binding.port() + " is active and exposed",
                "Disable debug in production or bind to localhost only",
                binding
            );
        } else {
            return SecurityFlag.info(
                Category.DEBUG,
                "Debug Port Active",
                "Debug port " + binding.port() + " is active (localhost only)",
                "Ensure debug is disabled in production",
                binding
            );
        }
    }

    /**
     * Quick check if a binding has any critical or high severity issues.
     */
    public boolean hasCriticalIssues(PortBinding binding) {
        return analyze(binding).stream()
            .anyMatch(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.HIGH);
    }

    /**
     * Gets recommendations for a port binding.
     */
    public List<String> getRecommendations(PortBinding binding) {
        return analyze(binding).stream()
            .map(SecurityFlag::recommendation)
            .distinct()
            .toList();
    }

    /**
     * Returns a quick security summary.
     */
    public String quickSummary(List<PortBinding> bindings) {
        SecurityReport report = analyze(bindings);

        if (report.isClean()) {
            return "No security issues found";
        }

        return report.summary();
    }
}
