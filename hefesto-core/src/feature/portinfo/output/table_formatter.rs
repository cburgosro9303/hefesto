use crossterm::style::{Color, Stylize};

use hefesto_domain::config::HefestoConfig;
use hefesto_domain::portinfo::enriched_port_binding::EnrichedPortBinding;
use hefesto_domain::portinfo::health_check::HealthCheckResult;
use hefesto_domain::portinfo::network_overview::NetworkOverview;
use hefesto_domain::portinfo::port_binding::ConnectionState;
use hefesto_domain::portinfo::security::{SecurityReport, Severity};

use super::OutputFormatter;

/// Formats output as ASCII tables with optional crossterm colors.
pub struct TableFormatter {
    use_colors: bool,
}

impl TableFormatter {
    /// Creates a new `TableFormatter`.
    pub fn new(use_colors: bool) -> Self {
        Self { use_colors }
    }

    fn colorize(&self, text: &str, color: Color) -> String {
        if self.use_colors {
            text.with(color).to_string()
        } else {
            text.to_string()
        }
    }

    fn bold(&self, text: &str) -> String {
        if self.use_colors {
            text.bold().to_string()
        } else {
            text.to_string()
        }
    }

    fn underline(&self, text: &str) -> String {
        if self.use_colors {
            text.underlined().to_string()
        } else {
            text.to_string()
        }
    }

    fn colorize_state(&self, state: &ConnectionState) -> String {
        let text = state.as_str();
        if !self.use_colors {
            return text.to_string();
        }
        match state {
            ConnectionState::Listen => text.with(Color::Green).to_string(),
            ConnectionState::Established => text.with(Color::Blue).to_string(),
            ConnectionState::TimeWait | ConnectionState::CloseWait => {
                text.with(Color::Yellow).to_string()
            }
            _ => text.to_string(),
        }
    }

    fn extract_cn(dn: &str) -> &str {
        if dn.is_empty() {
            return "";
        }
        for part in dn.split(',') {
            let trimmed = part.trim();
            if trimmed.to_uppercase().starts_with("CN=") {
                return &trimmed[3..];
            }
        }
        if dn.len() > 50 {
            &dn[..47]
        } else {
            dn
        }
    }
}

impl Default for TableFormatter {
    fn default() -> Self {
        Self::new(true)
    }
}

impl OutputFormatter for TableFormatter {
    fn format(&self, bindings: &[EnrichedPortBinding]) -> String {
        if bindings.is_empty() {
            return "No port bindings found.".to_string();
        }

        let config = HefestoConfig::get();
        let max_process_width = config.max_process_name_width;
        let max_service_width = config.max_service_width;

        let proto_width = 5;
        let port_width = 6;
        let state_width = 12;
        let address_width = 15;
        let pid_width = 8;
        let mut process_width: usize = 15;
        let mut service_width: usize = 15;

        for b in bindings {
            let proc_len = b.process_name().len();
            let capped = if max_process_width > 0 {
                proc_len.min(max_process_width as usize)
            } else {
                proc_len
            };
            process_width = process_width.max(capped);

            if let Some(ref si) = b.service_info {
                let svc_len = si.name.len();
                let capped = if max_service_width > 0 {
                    svc_len.min(max_service_width as usize)
                } else {
                    svc_len
                };
                service_width = service_width.max(capped);
            }
        }

        let mut sb = String::new();

        // Header
        let header = format!(
            "{:<proto_w$}  {:<port_w$}  {:<state_w$}  {:<addr_w$}  {:<pid_w$}  {:<proc_w$}  {:<svc_w$}\n",
            "PROTO", "PORT", "STATE", "ADDRESS", "PID", "PROCESS", "SERVICE",
            proto_w = proto_width,
            port_w = port_width,
            state_w = state_width,
            addr_w = address_width,
            pid_w = pid_width,
            proc_w = process_width,
            svc_w = service_width,
        );
        let separator_len = header.len().saturating_sub(1);

        if self.use_colors {
            sb.push_str(&self.bold(&header));
        } else {
            sb.push_str(&header);
        }
        sb.push_str(&"-".repeat(separator_len));
        sb.push('\n');

        // Rows
        for b in bindings {
            let proto = b.protocol().as_str();
            let port = b.port().to_string();
            let state = b.state().as_str().to_string();
            let address = b.local_address().to_string();
            let pid = b.pid().to_string();
            let process = config.truncate_process_name(b.process_name());
            let service = b
                .service_info
                .as_ref()
                .map(|si| config.truncate_service(&si.name))
                .unwrap_or_else(|| "-".to_string());

            let proto_str = self.colorize(proto, Color::Cyan);
            let state_str = self.colorize_state(b.state());
            let process_str = self.colorize(&process, Color::Green);
            let address_str = if self.use_colors && b.is_exposed() {
                self.colorize(&address, Color::Yellow)
            } else {
                address.clone()
            };

            // For alignment, we format with the raw lengths then replace
            // with colored strings. Since ANSI codes add invisible chars,
            // we pad manually.
            sb.push_str(&format!(
                "{proto_str}{pad_proto}  {port:<port_w$}  {state_str}{pad_state}  {address_str}{pad_addr}  {pid:<pid_w$}  {process_str}{pad_proc}  {service}\n",
                pad_proto = " ".repeat(proto_width.saturating_sub(proto.len())),
                port_w = port_width,
                pad_state = " ".repeat(state_width.saturating_sub(state.len())),
                pad_addr = " ".repeat(address_width.saturating_sub(address.len())),
                pid_w = pid_width,
                pad_proc = " ".repeat(process_width.saturating_sub(process.len())),
            ));
        }

        sb
    }

    fn format_overview(&self, overview: &NetworkOverview) -> String {
        let mut sb = String::new();

        // Title
        let title = "NETWORK OVERVIEW\n";
        sb.push_str(&self.bold(&self.underline(title)));
        sb.push_str("================\n\n");

        // Statistics
        sb.push_str("STATISTICS\n");
        let stats = &overview.statistics;
        sb.push_str(&format!(
            "  Listening:     {} ports\n",
            stats.total_listening
        ));
        sb.push_str(&format!(
            "  Established:   {} connections\n",
            stats.total_established
        ));
        sb.push_str(&format!(
            "  TCP: {} | UDP: {}\n",
            stats.tcp_count, stats.udp_count
        ));
        sb.push_str(&format!(
            "  Exposed (0.0.0.0): {} | Local (127.0.0.1): {}\n\n",
            stats.exposed_count, stats.local_only_count
        ));

        // Exposed ports
        let exposed = overview.exposed_ports();
        if !exposed.is_empty() {
            sb.push_str("EXPOSED PORTS (Network Accessible)\n");
            for b in &exposed {
                let service_tag = b
                    .service_info
                    .as_ref()
                    .map(|si| si.to_tag())
                    .unwrap_or_default();
                let line = format!(
                    "  {} :{:<6} {:<12} {:<15} pid={}\n",
                    b.protocol(),
                    b.port(),
                    b.process_name(),
                    service_tag,
                    b.pid()
                );
                if self.use_colors {
                    sb.push_str(&self.colorize(&line, Color::Yellow));
                } else {
                    sb.push_str(&line);
                }
            }
            sb.push('\n');
        }

        // Group by process
        let by_pid = overview.by_pid();
        if !by_pid.is_empty() {
            sb.push_str("PROCESSES\n");
            let mut pids: Vec<u32> = by_pid.keys().copied().collect();
            pids.sort();

            for pid in pids {
                if let Some(ports) = by_pid.get(&pid) {
                    if ports.is_empty() {
                        continue;
                    }
                    let first = ports[0];

                    let mut header_line =
                        format!("  {} (pid {})", first.process_name(), first.pid());
                    if !first.user().is_empty() {
                        header_line.push_str(&format!(" - {}", first.user()));
                    }

                    sb.push_str(&self.colorize(&header_line, Color::Green));
                    sb.push('\n');

                    for b in ports {
                        let service_tag = b
                            .service_info
                            .as_ref()
                            .map(|si| si.to_tag())
                            .unwrap_or_default();
                        let exposure = if b.is_exposed() {
                            "0.0.0.0"
                        } else if b.is_local_only() {
                            "127.0.0.1"
                        } else {
                            b.local_address()
                        };
                        sb.push_str(&format!(
                            "    :{}  {}  {}  {}\n",
                            b.port(),
                            b.state(),
                            exposure,
                            service_tag
                        ));
                    }
                }
            }
        }

        sb
    }

    fn format_security_report(&self, report: &SecurityReport) -> String {
        let mut sb = String::new();

        // Title
        let title = "SECURITY ANALYSIS\n";
        sb.push_str(&self.bold(&self.underline(title)));
        sb.push_str("=================\n\n");

        if report.is_clean() {
            sb.push_str(&self.colorize("No security issues found.\n", Color::Green));
            return sb;
        }

        // Group by severity
        let severity_order = [
            Severity::Critical,
            Severity::High,
            Severity::Warning,
            Severity::Info,
        ];

        for severity in &severity_order {
            let flags: Vec<_> = report
                .findings
                .iter()
                .filter(|f| &f.severity == severity)
                .collect();
            if flags.is_empty() {
                continue;
            }

            let color = match severity {
                Severity::Critical | Severity::High => Color::Red,
                Severity::Warning => Color::Yellow,
                Severity::Info => Color::Cyan,
            };

            let header = format!(
                "{} ({})\n",
                severity.display_name().to_uppercase(),
                flags.len()
            );
            sb.push_str(&self.colorize(&self.bold(&header), color));

            for flag in &flags {
                let line = format!("  {}\n", flag.to_summary_line());
                sb.push_str(&self.colorize(&line, color));
            }
            sb.push('\n');
        }

        // Summary
        sb.push_str(&format!("SUMMARY: {}\n", report.summary()));

        sb
    }

    fn format_health_check(&self, result: &HealthCheckResult) -> String {
        let mut sb = String::new();

        // Title
        let title = format!("PORT {} HEALTH CHECK\n", result.port);
        sb.push_str(&self.bold(&self.underline(&title)));
        sb.push_str("======================\n");

        // TCP status
        let status_color = if result.is_healthy() {
            Color::Green
        } else {
            Color::Red
        };

        let status_text = format!(
            "{} ({}ms)",
            result.status.display_name().to_uppercase(),
            result.response_time_ms
        );
        sb.push_str("TCP:     ");
        sb.push_str(&self.colorize(&status_text, status_color));
        sb.push('\n');

        // HTTP info if available
        if let Some(ref http) = result.http_info {
            let http_color = if http.is_success() {
                Color::Green
            } else {
                Color::Yellow
            };

            let http_text = format!("{} ({}ms)", http.status_formatted(), http.response_time_ms);
            sb.push_str("HTTP:    ");
            sb.push_str(&self.colorize(&http_text, http_color));
            sb.push('\n');

            if !http.content_type.is_empty() {
                sb.push_str(&format!(
                    "Content: {} ({})\n",
                    http.content_type,
                    http.content_length_formatted()
                ));
            }
        }

        // SSL info if available
        if let Some(ref ssl) = result.ssl_info {
            sb.push_str("\nSSL CERTIFICATE\n");
            sb.push_str(&format!("  Subject:  {}\n", Self::extract_cn(&ssl.subject)));
            sb.push_str(&format!("  Issuer:   {}\n", Self::extract_cn(&ssl.issuer)));
            sb.push_str(&format!("  Protocol: {}\n", ssl.protocol));

            let days_left = ssl.days_until_expiry();
            let expiry_color = if ssl.is_expired() {
                Color::Red
            } else if ssl.expires_soon() {
                Color::Yellow
            } else {
                Color::Green
            };

            let expiry_text = if ssl.is_expired() {
                "EXPIRED".to_string()
            } else if days_left >= 0 {
                format!("{days_left} days")
            } else {
                "Unknown".to_string()
            };

            sb.push_str("  Expires:  ");
            sb.push_str(&self.colorize(&expiry_text, expiry_color));
            sb.push('\n');
        }

        sb
    }

    fn mime_type(&self) -> &str {
        "text/plain"
    }

    fn file_extension(&self) -> &str {
        "txt"
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use hefesto_domain::portinfo::port_binding::{PortBinding, Protocol};

    #[test]
    fn test_format_empty() {
        let formatter = TableFormatter::new(false);
        let result = formatter.format(&[]);
        assert_eq!(result, "No port bindings found.");
    }

    #[test]
    fn test_format_single_binding() {
        let formatter = TableFormatter::new(false);
        let binding = PortBinding::listen(8080, Protocol::Tcp, 1234, "java");
        let enriched = EnrichedPortBinding::from_binding(binding);
        let result = formatter.format(&[enriched]);
        assert!(result.contains("PROTO"));
        assert!(result.contains("8080"));
        assert!(result.contains("java"));
        assert!(result.contains("LISTEN"));
    }

    #[test]
    fn test_extract_cn() {
        assert_eq!(
            TableFormatter::extract_cn("CN=example.com,O=Org"),
            "example.com"
        );
        assert_eq!(TableFormatter::extract_cn(""), "");
    }

    #[test]
    fn test_format_health_check() {
        use hefesto_domain::portinfo::health_check::HealthStatus;

        let formatter = TableFormatter::new(false);
        let result = HealthCheckResult::tcp(8080, HealthStatus::Reachable, 42, "OK");
        let output = formatter.format_health_check(&result);
        assert!(output.contains("8080"));
        assert!(output.contains("REACHABLE"));
        assert!(output.contains("42ms"));
    }

    #[test]
    fn test_format_security_clean() {
        use hefesto_domain::portinfo::security::SecurityReport;

        let formatter = TableFormatter::new(false);
        let report = SecurityReport::empty();
        let output = formatter.format_security_report(&report);
        assert!(output.contains("No security issues found"));
    }
}
