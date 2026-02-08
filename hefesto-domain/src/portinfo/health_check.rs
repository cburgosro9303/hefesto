use chrono::{DateTime, Utc};
use serde::Serialize;

/// Health check status.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub enum HealthStatus {
    Reachable,
    Unreachable,
    Timeout,
    Refused,
    Error,
}

impl HealthStatus {
    pub fn display_name(&self) -> &str {
        match self {
            HealthStatus::Reachable => "Reachable",
            HealthStatus::Unreachable => "Unreachable",
            HealthStatus::Timeout => "Timeout",
            HealthStatus::Refused => "Connection Refused",
            HealthStatus::Error => "Error",
        }
    }

    pub fn is_healthy(&self) -> bool {
        matches!(self, HealthStatus::Reachable)
    }
}

impl std::fmt::Display for HealthStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.display_name())
    }
}

/// HTTP-specific health information.
#[derive(Debug, Clone, Serialize)]
pub struct HttpInfo {
    pub status_code: u16,
    pub status_text: String,
    pub content_type: String,
    pub content_length: i64,
    pub response_time_ms: u64,
}

impl HttpInfo {
    /// Checks if HTTP status is successful (2xx).
    pub fn is_success(&self) -> bool {
        (200..300).contains(&self.status_code)
    }

    /// Returns formatted status: "200 OK"
    pub fn status_formatted(&self) -> String {
        format!("{} {}", self.status_code, self.status_text)
    }

    /// Returns content length in human-readable format.
    pub fn content_length_formatted(&self) -> String {
        if self.content_length < 0 {
            "unknown".to_string()
        } else if self.content_length < 1024 {
            format!("{} bytes", self.content_length)
        } else if self.content_length < 1024 * 1024 {
            format!("{:.1} KB", self.content_length as f64 / 1024.0)
        } else {
            format!("{:.2} MB", self.content_length as f64 / (1024.0 * 1024.0))
        }
    }
}

/// SSL certificate information.
#[derive(Debug, Clone, Serialize)]
pub struct SslInfo {
    pub issuer: String,
    pub subject: String,
    pub valid_from: Option<DateTime<Utc>>,
    pub valid_to: Option<DateTime<Utc>>,
    pub protocol: String,
    pub cipher_suite: String,
    pub valid: bool,
}

impl SslInfo {
    /// Returns days until expiration.
    pub fn days_until_expiry(&self) -> i64 {
        match self.valid_to {
            Some(valid_to) => (valid_to - Utc::now()).num_days(),
            None => -1,
        }
    }

    /// Checks if certificate is expired.
    pub fn is_expired(&self) -> bool {
        self.valid_to.is_some_and(|vt| Utc::now() > vt)
    }

    /// Checks if certificate expires soon (within 30 days).
    pub fn expires_soon(&self) -> bool {
        let days = self.days_until_expiry();
        (0..=30).contains(&days)
    }
}

/// Result of a health check.
#[derive(Debug, Clone, Serialize)]
pub struct HealthCheckResult {
    pub port: u16,
    pub protocol: String,
    pub status: HealthStatus,
    pub response_time_ms: u64,
    pub message: String,
    pub http_info: Option<HttpInfo>,
    pub ssl_info: Option<SslInfo>,
    pub timestamp: DateTime<Utc>,
}

impl HealthCheckResult {
    /// Creates a TCP-only health check result.
    pub fn tcp(
        port: u16,
        status: HealthStatus,
        response_time_ms: u64,
        message: impl Into<String>,
    ) -> Self {
        Self {
            port,
            protocol: "TCP".to_string(),
            status,
            response_time_ms,
            message: message.into(),
            http_info: None,
            ssl_info: None,
            timestamp: Utc::now(),
        }
    }

    /// Creates an HTTP health check result.
    pub fn http(
        port: u16,
        status: HealthStatus,
        response_time_ms: u64,
        http_info: HttpInfo,
    ) -> Self {
        Self {
            port,
            protocol: "HTTP".to_string(),
            status,
            response_time_ms,
            message: String::new(),
            http_info: Some(http_info),
            ssl_info: None,
            timestamp: Utc::now(),
        }
    }

    /// Creates an SSL health check result.
    pub fn ssl(port: u16, status: HealthStatus, response_time_ms: u64, ssl_info: SslInfo) -> Self {
        Self {
            port,
            protocol: "SSL".to_string(),
            status,
            response_time_ms,
            message: String::new(),
            http_info: None,
            ssl_info: Some(ssl_info),
            timestamp: Utc::now(),
        }
    }

    /// Checks if the health check was successful.
    pub fn is_healthy(&self) -> bool {
        self.status.is_healthy()
    }
}
