use std::collections::HashSet;

use hefesto_domain::portinfo::enriched_port_binding::EnrichedPortBinding;
use hefesto_domain::portinfo::port_binding::PortBinding;
use hefesto_domain::portinfo::security::{
    SecurityCategory, SecurityFlag, SecurityReport, Severity,
};

use super::service_registry::ServiceRegistry;

/// Debug ports that should never be exposed to the network.
const DEBUG_PORTS: &[u16] = &[5005, 9229, 5858, 4000];

/// Database ports that are risky when exposed.
const DATABASE_PORTS: &[u16] = &[3306, 5432, 27017, 6379, 1521, 1433, 9042, 5984];

/// Admin/management interfaces that should be local-only.
const ADMIN_PORTS: &[u16] = &[15672, 9090, 8500, 8200, 8761, 9411, 16686];

/// Privileged user names that indicate elevated execution.
const PRIVILEGED_USERS: &[&str] = &["root", "SYSTEM", "Administrator", "LocalSystem"];

/// Service for analyzing port bindings for security issues.
///
/// Inspects port bindings for common security misconfigurations such as
/// network-exposed databases, active debug ports, privileged process
/// execution, and exposed administrative interfaces.
pub struct SecurityAnalysisService {
    service_registry: ServiceRegistry,
    debug_ports: HashSet<u16>,
    database_ports: HashSet<u16>,
    admin_ports: HashSet<u16>,
    privileged_users: HashSet<String>,
}

impl SecurityAnalysisService {
    /// Creates a new `SecurityAnalysisService` with the default service registry.
    pub fn new() -> Self {
        Self::with_registry(ServiceRegistry::new())
    }

    /// Creates a new `SecurityAnalysisService` with a custom service registry.
    pub fn with_registry(service_registry: ServiceRegistry) -> Self {
        Self {
            service_registry,
            debug_ports: DEBUG_PORTS.iter().copied().collect(),
            database_ports: DATABASE_PORTS.iter().copied().collect(),
            admin_ports: ADMIN_PORTS.iter().copied().collect(),
            privileged_users: PRIVILEGED_USERS.iter().map(|s| s.to_string()).collect(),
        }
    }

    /// Analyzes a single port binding for security issues.
    pub fn analyze_binding(&self, binding: &PortBinding) -> Vec<SecurityFlag> {
        let mut flags = Vec::new();

        // Check for network exposure
        if self.is_exposed(binding) {
            flags.extend(self.analyze_exposed_port(binding));
        }

        // Check for privileged execution
        if self.is_running_as_privileged(binding) {
            flags.push(self.analyze_privileged_process(binding));
        }

        // Check for debug ports
        if self.is_debug_port(binding.port) {
            flags.push(self.analyze_debug_port(binding));
        }

        flags
    }

    /// Analyzes multiple port bindings and generates a security report.
    pub fn analyze(&self, bindings: &[PortBinding]) -> SecurityReport {
        let mut all_flags: Vec<SecurityFlag> = Vec::new();

        for binding in bindings {
            all_flags.extend(self.analyze_binding(binding));
        }

        // Sort by severity (highest first)
        all_flags.sort_by(|a, b| b.severity.level().cmp(&a.severity.level()));

        SecurityReport::new(all_flags, bindings.len())
    }

    /// Analyzes enriched port bindings (extracts raw bindings first).
    pub fn analyze_enriched(&self, bindings: &[EnrichedPortBinding]) -> SecurityReport {
        let raw: Vec<PortBinding> = bindings.iter().map(|eb| eb.binding.clone()).collect();
        self.analyze(&raw)
    }

    /// Quick check if a binding has any critical or high severity issues.
    pub fn has_critical_issues(&self, binding: &PortBinding) -> bool {
        self.analyze_binding(binding)
            .iter()
            .any(|f| matches!(f.severity, Severity::Critical | Severity::High))
    }

    /// Gets recommendations for a port binding.
    pub fn get_recommendations(&self, binding: &PortBinding) -> Vec<String> {
        self.analyze_binding(binding)
            .into_iter()
            .map(|f| f.recommendation)
            .collect::<Vec<_>>()
            .into_iter()
            .collect::<std::collections::HashSet<_>>()
            .into_iter()
            .collect()
    }

    /// Returns a quick security summary.
    pub fn quick_summary(&self, bindings: &[PortBinding]) -> String {
        let report = self.analyze(bindings);

        if report.is_clean() {
            "No security issues found".to_string()
        } else {
            report.summary()
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    fn is_exposed(&self, binding: &PortBinding) -> bool {
        let addr = &binding.local_address;
        addr == "0.0.0.0" || addr == "::" || addr == "*"
    }

    fn is_running_as_privileged(&self, binding: &PortBinding) -> bool {
        self.privileged_users.contains(&binding.user)
    }

    fn is_debug_port(&self, port: u16) -> bool {
        self.debug_ports.contains(&port)
    }

    fn is_database_port(&self, port: u16) -> bool {
        self.database_ports.contains(&port)
    }

    fn is_admin_port(&self, port: u16) -> bool {
        self.admin_ports.contains(&port)
    }

    fn analyze_exposed_port(&self, binding: &PortBinding) -> Vec<SecurityFlag> {
        let mut flags = Vec::new();
        let port = binding.port;

        if self.is_database_port(port) {
            // Database exposed
            let service_name = self
                .service_registry
                .lookup_tcp(port)
                .map(|s| s.name.clone())
                .unwrap_or_else(|| "Database".to_string());

            let severity = if self.is_running_as_privileged(binding) {
                Severity::Critical
            } else {
                Severity::High
            };

            let title = format!("{service_name} Exposed");
            let mut description = "Database port exposed to network".to_string();
            let mut recommendation =
                "Bind to 127.0.0.1 or use firewall rules to restrict access".to_string();

            if self.is_running_as_privileged(binding) {
                description.push_str(&format!(" and running as {}", binding.user));
                recommendation.push_str(". Also consider running as non-privileged user.");
            }

            flags.push(SecurityFlag {
                severity,
                category: SecurityCategory::Database,
                title,
                description,
                recommendation,
                related_port: binding.port,
                related_process: binding.process_name.clone(),
            });
        } else if self.is_debug_port(port) {
            // Debug port exposed
            flags.push(SecurityFlag::critical(
                SecurityCategory::Debug,
                "Debug Port Exposed",
                format!(
                    "Debug port {port} exposed to network - allows remote code execution"
                ),
                format!(
                    "Bind debug port to 127.0.0.1 only: -agentlib:jdwp=...,address=127.0.0.1:{port}"
                ),
                binding,
            ));
        } else if self.is_admin_port(port) {
            // Admin interface exposed
            let service_name = self
                .service_registry
                .lookup_tcp(port)
                .map(|s| s.name.clone())
                .unwrap_or_else(|| "Admin interface".to_string());

            flags.push(SecurityFlag::high(
                SecurityCategory::Configuration,
                format!("{service_name} Exposed"),
                "Administrative interface exposed to network",
                "Bind to 127.0.0.1 or use authentication and firewall rules",
                binding,
            ));
        } else {
            // Generic network exposure warning
            let service_name = self
                .service_registry
                .lookup_tcp(port)
                .map(|s| s.name.clone())
                .unwrap_or_else(|| "Service".to_string());

            flags.push(SecurityFlag::warning(
                SecurityCategory::NetworkExposure,
                format!("{service_name} Network Exposed"),
                format!("Port {port} is accessible from network (0.0.0.0)"),
                "Consider binding to 127.0.0.1 if network access is not required",
                binding,
            ));
        }

        flags
    }

    fn analyze_privileged_process(&self, binding: &PortBinding) -> SecurityFlag {
        // Higher severity for user-facing ports above 1024
        let mut severity = if binding.port < 1024 {
            Severity::Warning
        } else {
            Severity::High
        };

        if self.is_database_port(binding.port) {
            severity = Severity::High;
        }

        SecurityFlag {
            severity,
            category: SecurityCategory::Privilege,
            title: format!("Running as {}", binding.user),
            description: format!(
                "{} (port {}) running with elevated privileges",
                binding.process_name, binding.port
            ),
            recommendation:
                "Run as non-privileged user. Use systemd socket activation or capabilities for low ports."
                    .to_string(),
            related_port: binding.port,
            related_process: binding.process_name.clone(),
        }
    }

    fn analyze_debug_port(&self, binding: &PortBinding) -> SecurityFlag {
        if self.is_exposed(binding) {
            SecurityFlag::critical(
                SecurityCategory::Debug,
                "Debug Port Active",
                format!(
                    "Debug port {} is active and exposed",
                    binding.port
                ),
                "Disable debug in production or bind to localhost only",
                binding,
            )
        } else {
            SecurityFlag::info(
                SecurityCategory::Debug,
                "Debug Port Active",
                format!(
                    "Debug port {} is active (localhost only)",
                    binding.port
                ),
                "Ensure debug is disabled in production",
                binding,
            )
        }
    }
}

impl Default for SecurityAnalysisService {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use hefesto_domain::portinfo::port_binding::{ConnectionState, Protocol};

    fn make_binding(port: u16, addr: &str, user: &str, name: &str) -> PortBinding {
        PortBinding {
            port,
            protocol: Protocol::Tcp,
            state: ConnectionState::Listen,
            pid: 100,
            process_name: name.to_string(),
            command_line: String::new(),
            user: user.to_string(),
            local_address: addr.to_string(),
            remote_address: String::new(),
            remote_port: 0,
        }
    }

    #[test]
    fn test_clean_report() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(8080, "127.0.0.1", "appuser", "java");
        let report = svc.analyze(&[binding]);
        assert!(report.is_clean());
    }

    #[test]
    fn test_exposed_database_high() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(3306, "0.0.0.0", "mysql", "mysqld");
        let flags = svc.analyze_binding(&binding);
        assert!(!flags.is_empty());
        assert!(flags.iter().any(|f| f.severity == Severity::High));
    }

    #[test]
    fn test_exposed_database_critical_when_root() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(3306, "0.0.0.0", "root", "mysqld");
        let flags = svc.analyze_binding(&binding);
        assert!(flags.iter().any(|f| f.severity == Severity::Critical));
    }

    #[test]
    fn test_exposed_debug_port() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(5005, "0.0.0.0", "user", "java");
        let flags = svc.analyze_binding(&binding);
        // Should have both exposed-debug and active-debug flags
        assert!(flags.iter().any(|f| f.severity == Severity::Critical));
    }

    #[test]
    fn test_local_debug_port_info() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(5005, "127.0.0.1", "user", "java");
        let flags = svc.analyze_binding(&binding);
        assert!(flags.iter().any(|f| f.severity == Severity::Info));
    }

    #[test]
    fn test_exposed_admin_port() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(15672, "0.0.0.0", "rabbitmq", "rabbitmq");
        let flags = svc.analyze_binding(&binding);
        assert!(flags.iter().any(|f| f.severity == Severity::High));
    }

    #[test]
    fn test_privileged_process_high_port() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(8080, "127.0.0.1", "root", "java");
        let flags = svc.analyze_binding(&binding);
        assert!(flags.iter().any(|f| f.category == SecurityCategory::Privilege));
        assert!(flags.iter().any(|f| f.severity == Severity::High));
    }

    #[test]
    fn test_privileged_process_low_port() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(80, "127.0.0.1", "root", "nginx");
        let flags = svc.analyze_binding(&binding);
        let priv_flag = flags.iter().find(|f| f.category == SecurityCategory::Privilege);
        assert!(priv_flag.is_some());
        assert_eq!(priv_flag.unwrap().severity, Severity::Warning);
    }

    #[test]
    fn test_has_critical_issues() {
        let svc = SecurityAnalysisService::new();
        let safe = make_binding(8080, "127.0.0.1", "user", "java");
        let dangerous = make_binding(5005, "0.0.0.0", "root", "java");
        assert!(!svc.has_critical_issues(&safe));
        assert!(svc.has_critical_issues(&dangerous));
    }

    #[test]
    fn test_quick_summary_clean() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(8080, "127.0.0.1", "user", "java");
        let summary = svc.quick_summary(&[binding]);
        assert_eq!(summary, "No security issues found");
    }

    #[test]
    fn test_quick_summary_with_issues() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(3306, "0.0.0.0", "root", "mysqld");
        let summary = svc.quick_summary(&[binding]);
        assert!(summary.contains("critical"));
    }

    #[test]
    fn test_generic_exposure_warning() {
        let svc = SecurityAnalysisService::new();
        let binding = make_binding(12345, "0.0.0.0", "user", "custom");
        let flags = svc.analyze_binding(&binding);
        assert!(flags.iter().any(|f| f.severity == Severity::Warning));
        assert!(flags
            .iter()
            .any(|f| f.category == SecurityCategory::NetworkExposure));
    }

    #[test]
    fn test_analyze_report_sorted() {
        let svc = SecurityAnalysisService::new();
        let bindings = vec![
            make_binding(12345, "0.0.0.0", "user", "custom"), // warning
            make_binding(5005, "0.0.0.0", "root", "java"),    // critical
        ];
        let report = svc.analyze(&bindings);
        let sorted = report.sorted_by_severity();
        // Critical should come first
        assert!(sorted[0].severity.level() >= sorted.last().unwrap().severity.level());
    }
}
