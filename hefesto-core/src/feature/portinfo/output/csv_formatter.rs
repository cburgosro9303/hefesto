use hefesto_domain::portinfo::enriched_port_binding::EnrichedPortBinding;
use hefesto_domain::portinfo::health_check::HealthCheckResult;
use hefesto_domain::portinfo::network_overview::NetworkOverview;
use hefesto_domain::portinfo::security::SecurityReport;

use super::OutputFormatter;

const DELIMITER: &str = ",";
const NEWLINE: &str = "\n";

/// Formats output as CSV.
///
/// Produces RFC 4180-compliant CSV with proper quoting for fields that
/// contain delimiters, newlines, or double quotes. Numbers are formatted
/// in a locale-independent manner (Rust default).
pub struct CsvFormatter;

impl CsvFormatter {
    /// Creates a new `CsvFormatter`.
    pub fn new() -> Self {
        Self
    }
}

impl Default for CsvFormatter {
    fn default() -> Self {
        Self::new()
    }
}

impl OutputFormatter for CsvFormatter {
    fn format(&self, bindings: &[EnrichedPortBinding]) -> String {
        let mut sb = String::new();

        // Header
        sb.push_str(&csv_row(&[
            "protocol",
            "port",
            "state",
            "local_address",
            "remote_address",
            "remote_port",
            "pid",
            "process_name",
            "user",
            "service",
            "category",
        ]));

        // Data rows
        for b in bindings {
            let service = b
                .service_info
                .as_ref()
                .map(|si| si.name.as_str())
                .unwrap_or("");
            let category = b
                .service_info
                .as_ref()
                .map(|si| si.category.display_name())
                .unwrap_or("");

            sb.push_str(&csv_row(&[
                b.protocol().as_str(),
                &b.port().to_string(),
                b.state().as_str(),
                b.local_address(),
                &b.binding.remote_address,
                &b.binding.remote_port.to_string(),
                &b.pid().to_string(),
                b.process_name(),
                b.user(),
                service,
                category,
            ]));
        }

        sb
    }

    fn format_overview(&self, overview: &NetworkOverview) -> String {
        let mut sb = String::new();

        // Statistics section
        sb.push_str("# Statistics");
        sb.push_str(NEWLINE);
        sb.push_str(&csv_row(&["metric", "value"]));

        let stats = &overview.statistics;
        sb.push_str(&csv_row(&["total_listening", &stats.total_listening.to_string()]));
        sb.push_str(&csv_row(&[
            "total_established",
            &stats.total_established.to_string(),
        ]));
        sb.push_str(&csv_row(&["tcp_count", &stats.tcp_count.to_string()]));
        sb.push_str(&csv_row(&["udp_count", &stats.udp_count.to_string()]));
        sb.push_str(&csv_row(&["exposed_count", &stats.exposed_count.to_string()]));
        sb.push_str(&csv_row(&[
            "local_only_count",
            &stats.local_only_count.to_string(),
        ]));
        sb.push_str(NEWLINE);

        // Port bindings section
        sb.push_str("# Port Bindings");
        sb.push_str(NEWLINE);
        sb.push_str(&self.format(&overview.bindings));

        sb
    }

    fn format_security_report(&self, report: &SecurityReport) -> String {
        let mut sb = String::new();

        // Summary
        sb.push_str("# Security Report Summary");
        sb.push_str(NEWLINE);
        sb.push_str(&csv_row(&["severity", "count"]));
        sb.push_str(&csv_row(&["critical", &report.critical_count().to_string()]));
        sb.push_str(&csv_row(&["high", &report.high_count().to_string()]));
        sb.push_str(&csv_row(&["warning", &report.warning_count().to_string()]));
        sb.push_str(&csv_row(&["info", &report.info_count().to_string()]));
        sb.push_str(NEWLINE);

        // Findings
        sb.push_str("# Findings");
        sb.push_str(NEWLINE);
        sb.push_str(&csv_row(&[
            "severity",
            "category",
            "title",
            "description",
            "recommendation",
            "port",
            "process",
        ]));

        for flag in report.sorted_by_severity() {
            sb.push_str(&csv_row(&[
                flag.severity.display_name(),
                flag.category.display_name(),
                &flag.title,
                &flag.description,
                &flag.recommendation,
                &flag.related_port.to_string(),
                &flag.related_process,
            ]));
        }

        sb
    }

    fn format_health_check(&self, result: &HealthCheckResult) -> String {
        let mut sb = String::new();

        sb.push_str(&csv_row(&["field", "value"]));
        sb.push_str(&csv_row(&["port", &result.port.to_string()]));
        sb.push_str(&csv_row(&["protocol", &result.protocol]));
        sb.push_str(&csv_row(&["status", &format!("{:?}", result.status)]));
        sb.push_str(&csv_row(&[
            "response_time_ms",
            &result.response_time_ms.to_string(),
        ]));
        sb.push_str(&csv_row(&["healthy", &result.is_healthy().to_string()]));

        if let Some(ref http) = result.http_info {
            sb.push_str(&csv_row(&[
                "http_status_code",
                &http.status_code.to_string(),
            ]));
            sb.push_str(&csv_row(&["http_status_text", &http.status_text]));
            sb.push_str(&csv_row(&[
                "content_type",
                if http.content_type.is_empty() {
                    ""
                } else {
                    &http.content_type
                },
            ]));
            sb.push_str(&csv_row(&[
                "content_length",
                &http.content_length.to_string(),
            ]));
        }

        if let Some(ref ssl) = result.ssl_info {
            sb.push_str(&csv_row(&["ssl_issuer", &ssl.issuer]));
            sb.push_str(&csv_row(&["ssl_subject", &ssl.subject]));
            sb.push_str(&csv_row(&["ssl_protocol", &ssl.protocol]));
            sb.push_str(&csv_row(&["ssl_valid", &ssl.valid.to_string()]));
            sb.push_str(&csv_row(&[
                "ssl_days_until_expiry",
                &ssl.days_until_expiry().to_string(),
            ]));
        }

        sb
    }

    fn mime_type(&self) -> &str {
        "text/csv"
    }

    fn file_extension(&self) -> &str {
        "csv"
    }
}

/// Builds a CSV row from an array of values.
fn csv_row(values: &[&str]) -> String {
    let mut parts = Vec::with_capacity(values.len());
    for value in values {
        parts.push(escape_csv(value));
    }
    let mut row = parts.join(DELIMITER);
    row.push_str(NEWLINE);
    row
}

/// Escapes a CSV value according to RFC 4180.
/// If the value contains a delimiter, newline, or double quote, it is
/// wrapped in double quotes with internal quotes doubled.
fn escape_csv(value: &str) -> String {
    if value.contains(DELIMITER) || value.contains('\n') || value.contains('"') {
        format!("\"{}\"", value.replace('"', "\"\""))
    } else {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use hefesto_domain::portinfo::health_check::HealthStatus;
    use hefesto_domain::portinfo::port_binding::{PortBinding, Protocol};
    use hefesto_domain::portinfo::security::SecurityReport;

    #[test]
    fn test_escape_csv_simple() {
        assert_eq!(escape_csv("hello"), "hello");
    }

    #[test]
    fn test_escape_csv_with_comma() {
        assert_eq!(escape_csv("hello,world"), "\"hello,world\"");
    }

    #[test]
    fn test_escape_csv_with_quote() {
        assert_eq!(escape_csv("say \"hi\""), "\"say \"\"hi\"\"\"");
    }

    #[test]
    fn test_escape_csv_with_newline() {
        assert_eq!(escape_csv("line1\nline2"), "\"line1\nline2\"");
    }

    #[test]
    fn test_csv_row() {
        assert_eq!(csv_row(&["a", "b", "c"]), "a,b,c\n");
    }

    #[test]
    fn test_format_empty() {
        let formatter = CsvFormatter::new();
        let result = formatter.format(&[]);
        // Should only have header
        assert!(result.starts_with("protocol,"));
        assert_eq!(result.lines().count(), 1);
    }

    #[test]
    fn test_format_single_binding() {
        let formatter = CsvFormatter::new();
        let binding = PortBinding::listen(8080, Protocol::Tcp, 1234, "java");
        let enriched = EnrichedPortBinding::from_binding(binding);
        let result = formatter.format(&[enriched]);
        let lines: Vec<&str> = result.lines().collect();
        assert_eq!(lines.len(), 2); // header + one row
        assert!(lines[1].contains("8080"));
        assert!(lines[1].contains("java"));
    }

    #[test]
    fn test_format_health_check() {
        let formatter = CsvFormatter::new();
        let result = HealthCheckResult::tcp(8080, HealthStatus::Reachable, 42, "OK");
        let output = formatter.format_health_check(&result);
        assert!(output.contains("8080"));
        assert!(output.contains("42"));
        assert!(output.contains("true")); // healthy
    }

    #[test]
    fn test_format_security_clean() {
        let formatter = CsvFormatter::new();
        let report = SecurityReport::empty();
        let output = formatter.format_security_report(&report);
        assert!(output.contains("critical,0"));
    }
}
