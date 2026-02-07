use serde_json::{json, Map, Value};

use hefesto_domain::portinfo::enriched_port_binding::EnrichedPortBinding;
use hefesto_domain::portinfo::health_check::HealthCheckResult;
use hefesto_domain::portinfo::network_overview::NetworkOverview;
use hefesto_domain::portinfo::security::SecurityReport;

use super::OutputFormatter;

/// Formats output as JSON with enhanced structure.
///
/// All numeric values are formatted in a locale-independent manner (Rust
/// guarantees this by default -- no locale-dependent number formatting).
pub struct JsonFormatter {
    pretty: bool,
}

impl JsonFormatter {
    /// Creates a new `JsonFormatter` with pretty printing.
    pub fn new() -> Self {
        Self { pretty: true }
    }

    /// Creates a new `JsonFormatter` with optional pretty printing.
    pub fn with_pretty(pretty: bool) -> Self {
        Self { pretty }
    }

    fn to_string(&self, value: &Value) -> String {
        if self.pretty {
            serde_json::to_string_pretty(value).unwrap_or_else(|_| "{}".to_string())
        } else {
            serde_json::to_string(value).unwrap_or_else(|_| "{}".to_string())
        }
    }
}

impl Default for JsonFormatter {
    fn default() -> Self {
        Self::new()
    }
}

impl OutputFormatter for JsonFormatter {
    fn format(&self, bindings: &[EnrichedPortBinding]) -> String {
        let array: Vec<Value> = bindings
            .iter()
            .map(|b| {
                let mut obj = json!({
                    "port": b.port(),
                    "protocol": b.protocol().as_str(),
                    "state": b.state().as_str(),
                    "localAddress": b.local_address(),
                    "remoteAddress": b.binding.remote_address,
                    "remotePort": b.binding.remote_port,
                    "pid": b.pid(),
                    "processName": b.process_name(),
                    "user": b.user(),
                    "commandLine": b.binding.command_line,
                    "isExposed": b.is_exposed(),
                    "isLocalOnly": b.is_local_only(),
                });

                if let Some(ref si) = b.service_info {
                    obj["service"] = json!({
                        "name": si.name,
                        "description": si.description,
                        "category": format!("{:?}", si.category),
                    });
                }

                if let Some(ref di) = b.docker_info {
                    obj["docker"] = json!({
                        "containerId": di.short_id(),
                        "containerName": di.container_name,
                        "image": di.image,
                        "status": di.status,
                    });
                }

                obj
            })
            .collect();

        let value = Value::Array(array);
        self.to_string(&value)
    }

    fn format_overview(&self, overview: &NetworkOverview) -> String {
        let stats = &overview.statistics;
        let mut root = json!({
            "statistics": {
                "totalListening": stats.total_listening,
                "totalEstablished": stats.total_established,
                "tcpCount": stats.tcp_count,
                "udpCount": stats.udp_count,
                "exposedCount": stats.exposed_count,
                "localOnlyCount": stats.local_only_count,
            },
            "generatedAt": overview.generated_at.to_rfc3339(),
        });

        // Bindings grouped by process
        let by_pid = overview.by_pid();
        let mut processes = Map::new();

        for (&pid, ports) in &by_pid {
            if ports.is_empty() {
                continue;
            }
            let first = ports[0];

            let ports_array: Vec<Value> = ports
                .iter()
                .map(|b| {
                    let mut port_obj = json!({
                        "port": b.port(),
                        "protocol": b.protocol().as_str(),
                        "state": b.state().as_str(),
                        "address": b.local_address(),
                        "exposed": b.is_exposed(),
                    });
                    if let Some(ref si) = b.service_info {
                        port_obj["service"] = json!(si.name);
                    }
                    port_obj
                })
                .collect();

            let process_node = json!({
                "pid": first.pid(),
                "name": first.process_name(),
                "user": first.user(),
                "ports": ports_array,
            });

            processes.insert(pid.to_string(), process_node);
        }

        root["processes"] = Value::Object(processes);

        // Exposed ports summary
        let exposed: Vec<Value> = overview
            .exposed_ports()
            .iter()
            .map(|b| {
                let mut node = json!({
                    "port": b.port(),
                    "process": b.process_name(),
                    "pid": b.pid(),
                });
                if let Some(ref si) = b.service_info {
                    node["service"] = json!(si.name);
                }
                node
            })
            .collect();

        root["exposedPorts"] = Value::Array(exposed);

        self.to_string(&root)
    }

    fn format_security_report(&self, report: &SecurityReport) -> String {
        let root = json!({
            "summary": {
                "critical": report.critical_count(),
                "high": report.high_count(),
                "warning": report.warning_count(),
                "info": report.info_count(),
                "totalPortsAnalyzed": report.total_ports_analyzed,
                "clean": report.is_clean(),
            },
            "generatedAt": report.generated_at.to_rfc3339(),
            "findings": report.sorted_by_severity().iter().map(|flag| {
                json!({
                    "severity": format!("{:?}", flag.severity),
                    "category": format!("{:?}", flag.category),
                    "title": flag.title,
                    "description": flag.description,
                    "recommendation": flag.recommendation,
                    "binding": {
                        "port": flag.related_port,
                        "process": flag.related_process,
                    },
                })
            }).collect::<Vec<Value>>(),
        });

        self.to_string(&root)
    }

    fn format_health_check(&self, result: &HealthCheckResult) -> String {
        let mut root = json!({
            "port": result.port,
            "protocol": result.protocol,
            "status": format!("{:?}", result.status),
            "statusDisplay": result.status.display_name(),
            "healthy": result.is_healthy(),
            "responseTimeMs": result.response_time_ms,
            "message": result.message,
            "timestamp": result.timestamp.to_rfc3339(),
        });

        if let Some(ref http) = result.http_info {
            root["http"] = json!({
                "statusCode": http.status_code,
                "statusText": http.status_text,
                "contentType": http.content_type,
                "contentLength": http.content_length,
                "responseTimeMs": http.response_time_ms,
                "success": http.is_success(),
            });
        }

        if let Some(ref ssl) = result.ssl_info {
            root["ssl"] = json!({
                "issuer": ssl.issuer,
                "subject": ssl.subject,
                "validFrom": ssl.valid_from.map(|dt| dt.to_rfc3339()),
                "validTo": ssl.valid_to.map(|dt| dt.to_rfc3339()),
                "protocol": ssl.protocol,
                "cipherSuite": ssl.cipher_suite,
                "valid": ssl.valid,
                "expired": ssl.is_expired(),
                "expiresSoon": ssl.expires_soon(),
                "daysUntilExpiry": ssl.days_until_expiry(),
            });
        }

        self.to_string(&root)
    }

    fn mime_type(&self) -> &str {
        "application/json"
    }

    fn file_extension(&self) -> &str {
        "json"
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use hefesto_domain::portinfo::health_check::HealthStatus;
    use hefesto_domain::portinfo::port_binding::{PortBinding, Protocol};
    use hefesto_domain::portinfo::security::SecurityReport;
    use hefesto_domain::portinfo::service_info::{ServiceCategory, ServiceInfo};

    #[test]
    fn test_format_empty() {
        let formatter = JsonFormatter::new();
        let result = formatter.format(&[]);
        assert_eq!(result.trim(), "[]");
    }

    #[test]
    fn test_format_single_binding() {
        let formatter = JsonFormatter::with_pretty(false);
        let binding = PortBinding::listen(8080, Protocol::Tcp, 1234, "java");
        let enriched = EnrichedPortBinding::from_binding(binding);
        let result = formatter.format(&[enriched]);

        let parsed: Value = serde_json::from_str(&result).unwrap();
        assert!(parsed.is_array());
        assert_eq!(parsed[0]["port"], 8080);
        assert_eq!(parsed[0]["processName"], "java");
    }

    #[test]
    fn test_format_with_service_info() {
        let formatter = JsonFormatter::with_pretty(false);
        let binding = PortBinding::listen(3306, Protocol::Tcp, 100, "mysqld");
        let enriched = EnrichedPortBinding::with_service(
            binding,
            ServiceInfo::new("MySQL", "MySQL Database", ServiceCategory::Database),
        );
        let result = formatter.format(&[enriched]);

        let parsed: Value = serde_json::from_str(&result).unwrap();
        assert_eq!(parsed[0]["service"]["name"], "MySQL");
    }

    #[test]
    fn test_format_health_check() {
        let formatter = JsonFormatter::with_pretty(false);
        let result = HealthCheckResult::tcp(8080, HealthStatus::Reachable, 42, "OK");
        let output = formatter.format_health_check(&result);

        let parsed: Value = serde_json::from_str(&output).unwrap();
        assert_eq!(parsed["port"], 8080);
        assert_eq!(parsed["healthy"], true);
        assert_eq!(parsed["responseTimeMs"], 42);
    }

    #[test]
    fn test_format_security_clean() {
        let formatter = JsonFormatter::with_pretty(false);
        let report = SecurityReport::empty();
        let output = formatter.format_security_report(&report);

        let parsed: Value = serde_json::from_str(&output).unwrap();
        assert_eq!(parsed["summary"]["clean"], true);
    }

    #[test]
    fn test_locale_independent_numbers() {
        // Rust does not use locale for number formatting, but verify explicitly
        let formatter = JsonFormatter::with_pretty(false);
        let result = HealthCheckResult::tcp(8080, HealthStatus::Reachable, 12345, "OK");
        let output = formatter.format_health_check(&result);
        assert!(output.contains("12345"));
        // Ensure no commas as thousand separators
        assert!(!output.contains("12,345"));
    }
}
