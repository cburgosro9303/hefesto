use chrono::{DateTime, Utc};
use serde::Serialize;

use super::port_binding::PortBinding;

/// Severity levels for security findings.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize)]
pub enum Severity {
    Critical,
    High,
    Warning,
    Info,
}

impl Severity {
    pub fn display_name(&self) -> &str {
        match self {
            Severity::Critical => "Critical",
            Severity::High => "High",
            Severity::Warning => "Warning",
            Severity::Info => "Info",
        }
    }

    pub fn level(&self) -> u8 {
        match self {
            Severity::Critical => 4,
            Severity::High => 3,
            Severity::Warning => 2,
            Severity::Info => 1,
        }
    }
}

impl std::fmt::Display for Severity {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.display_name())
    }
}

/// Categories of security findings.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize)]
pub enum SecurityCategory {
    NetworkExposure,
    Privilege,
    Debug,
    Database,
    Configuration,
    Protocol,
}

impl SecurityCategory {
    pub fn display_name(&self) -> &str {
        match self {
            SecurityCategory::NetworkExposure => "Network Exposure",
            SecurityCategory::Privilege => "Privilege",
            SecurityCategory::Debug => "Debug/Development",
            SecurityCategory::Database => "Database",
            SecurityCategory::Configuration => "Configuration",
            SecurityCategory::Protocol => "Protocol",
        }
    }
}

impl std::fmt::Display for SecurityCategory {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.display_name())
    }
}

/// A security flag or warning.
#[derive(Debug, Clone, Serialize)]
pub struct SecurityFlag {
    pub severity: Severity,
    pub category: SecurityCategory,
    pub title: String,
    pub description: String,
    pub recommendation: String,
    pub related_port: u16,
    pub related_process: String,
}

impl SecurityFlag {
    pub fn critical(
        category: SecurityCategory,
        title: impl Into<String>,
        description: impl Into<String>,
        recommendation: impl Into<String>,
        binding: &PortBinding,
    ) -> Self {
        Self {
            severity: Severity::Critical,
            category,
            title: title.into(),
            description: description.into(),
            recommendation: recommendation.into(),
            related_port: binding.port,
            related_process: binding.process_name.clone(),
        }
    }

    pub fn high(
        category: SecurityCategory,
        title: impl Into<String>,
        description: impl Into<String>,
        recommendation: impl Into<String>,
        binding: &PortBinding,
    ) -> Self {
        Self {
            severity: Severity::High,
            category,
            title: title.into(),
            description: description.into(),
            recommendation: recommendation.into(),
            related_port: binding.port,
            related_process: binding.process_name.clone(),
        }
    }

    pub fn warning(
        category: SecurityCategory,
        title: impl Into<String>,
        description: impl Into<String>,
        recommendation: impl Into<String>,
        binding: &PortBinding,
    ) -> Self {
        Self {
            severity: Severity::Warning,
            category,
            title: title.into(),
            description: description.into(),
            recommendation: recommendation.into(),
            related_port: binding.port,
            related_process: binding.process_name.clone(),
        }
    }

    pub fn info(
        category: SecurityCategory,
        title: impl Into<String>,
        description: impl Into<String>,
        recommendation: impl Into<String>,
        binding: &PortBinding,
    ) -> Self {
        Self {
            severity: Severity::Info,
            category,
            title: title.into(),
            description: description.into(),
            recommendation: recommendation.into(),
            related_port: binding.port,
            related_process: binding.process_name.clone(),
        }
    }

    /// Returns a formatted summary line.
    pub fn to_summary_line(&self) -> String {
        format!(
            ":{} {} [{}] - {}",
            self.related_port, self.related_process, self.title, self.description
        )
    }
}

/// A complete security analysis report.
#[derive(Debug, Clone, Serialize)]
pub struct SecurityReport {
    pub findings: Vec<SecurityFlag>,
    pub generated_at: DateTime<Utc>,
    pub total_ports_analyzed: usize,
}

impl SecurityReport {
    /// Creates an empty report.
    pub fn empty() -> Self {
        Self {
            findings: Vec::new(),
            generated_at: Utc::now(),
            total_ports_analyzed: 0,
        }
    }

    /// Creates a report with findings.
    pub fn new(findings: Vec<SecurityFlag>, total_ports: usize) -> Self {
        Self {
            findings,
            generated_at: Utc::now(),
            total_ports_analyzed: total_ports,
        }
    }

    /// Returns count of critical findings.
    pub fn critical_count(&self) -> usize {
        self.count_by_severity(&Severity::Critical)
    }

    /// Returns count of high severity findings.
    pub fn high_count(&self) -> usize {
        self.count_by_severity(&Severity::High)
    }

    /// Returns count of warning findings.
    pub fn warning_count(&self) -> usize {
        self.count_by_severity(&Severity::Warning)
    }

    /// Returns count of info findings.
    pub fn info_count(&self) -> usize {
        self.count_by_severity(&Severity::Info)
    }

    /// Counts findings by severity.
    pub fn count_by_severity(&self, severity: &Severity) -> usize {
        self.findings
            .iter()
            .filter(|f| &f.severity == severity)
            .count()
    }

    /// Checks if there are critical or high severity issues.
    pub fn has_critical_issues(&self) -> bool {
        self.critical_count() > 0 || self.high_count() > 0
    }

    /// Checks if the report is clean (no findings).
    pub fn is_clean(&self) -> bool {
        self.findings.is_empty()
    }

    /// Returns a summary line.
    pub fn summary(&self) -> String {
        format!(
            "{} critical, {} high, {} warnings, {} info",
            self.critical_count(),
            self.high_count(),
            self.warning_count(),
            self.info_count()
        )
    }

    /// Returns findings sorted by severity (highest first).
    pub fn sorted_by_severity(&self) -> Vec<&SecurityFlag> {
        let mut sorted: Vec<&SecurityFlag> = self.findings.iter().collect();
        sorted.sort_by(|a, b| b.severity.level().cmp(&a.severity.level()));
        sorted
    }

    /// Filters findings by minimum severity.
    pub fn filter_by_min_severity(&self, min_severity: &Severity) -> Vec<&SecurityFlag> {
        self.findings
            .iter()
            .filter(|f| f.severity.level() >= min_severity.level())
            .collect()
    }
}
