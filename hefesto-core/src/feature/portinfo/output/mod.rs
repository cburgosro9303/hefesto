pub mod csv_formatter;
pub mod json_formatter;
pub mod table_formatter;

use hefesto_domain::portinfo::enriched_port_binding::EnrichedPortBinding;
use hefesto_domain::portinfo::health_check::HealthCheckResult;
use hefesto_domain::portinfo::network_overview::NetworkOverview;
use hefesto_domain::portinfo::port_binding::PortBinding;
use hefesto_domain::portinfo::security::SecurityReport;

/// Trait for formatting port information output.
///
/// Mirrors the Java `OutputFormatter` sealed interface, providing methods to
/// format enriched bindings, network overviews, security reports, and health
/// check results as strings. Each implementation targets a specific output
/// format (table, JSON, CSV).
pub trait OutputFormatter: Send + Sync {
    /// Formats a list of enriched port bindings.
    fn format(&self, bindings: &[EnrichedPortBinding]) -> String;

    /// Formats a network overview with statistics.
    fn format_overview(&self, overview: &NetworkOverview) -> String;

    /// Formats a security report.
    fn format_security_report(&self, report: &SecurityReport) -> String;

    /// Formats a health check result.
    fn format_health_check(&self, result: &HealthCheckResult) -> String;

    /// Formats a list of raw port bindings by enriching them first.
    fn format_raw(&self, bindings: &[PortBinding]) -> String {
        let enriched: Vec<EnrichedPortBinding> = bindings
            .iter()
            .map(|b| EnrichedPortBinding::from_binding(b.clone()))
            .collect();
        self.format(&enriched)
    }

    /// Returns the MIME type for this format.
    fn mime_type(&self) -> &str;

    /// Returns the file extension for this format.
    fn file_extension(&self) -> &str;
}

/// Output format selection.
#[derive(Debug, Clone, PartialEq)]
pub enum OutputFormat {
    Table,
    Json,
    Csv,
}

impl OutputFormat {
    pub fn from_str_loose(s: &str) -> Self {
        match s.to_lowercase().as_str() {
            "json" => OutputFormat::Json,
            "csv" => OutputFormat::Csv,
            _ => OutputFormat::Table,
        }
    }
}
