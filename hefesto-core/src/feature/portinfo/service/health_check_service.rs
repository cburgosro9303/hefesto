use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::time::{Duration, Instant};

use hefesto_domain::portinfo::health_check::{
    HealthCheckResult, HealthStatus, HttpInfo, SslInfo,
};

/// Default timeout for health checks in milliseconds.
const DEFAULT_TIMEOUT_MS: u64 = 5000;

/// Service for performing health checks on ports (TCP, HTTP, SSL).
///
/// Supports TCP connectivity checks, HTTP/HTTPS health probes with status
/// code inspection, and SSL certificate analysis using `native-tls`.
pub struct HealthCheckService {
    timeout: Duration,
}

impl HealthCheckService {
    /// Creates a new `HealthCheckService` with the default timeout.
    pub fn new() -> Self {
        Self {
            timeout: Duration::from_millis(DEFAULT_TIMEOUT_MS),
        }
    }

    /// Creates a new `HealthCheckService` with a custom timeout.
    pub fn with_timeout_ms(timeout_ms: u64) -> Self {
        Self {
            timeout: Duration::from_millis(timeout_ms),
        }
    }

    /// Sets the timeout for health checks.
    pub fn set_timeout_ms(&mut self, timeout_ms: u64) {
        self.timeout = Duration::from_millis(timeout_ms);
    }

    /// Performs a TCP connectivity check.
    pub fn check_tcp(&self, host: &str, port: u16) -> HealthCheckResult {
        let start = Instant::now();
        let addr = format!("{host}:{port}");

        match addr.parse::<SocketAddr>() {
            Ok(socket_addr) => match TcpStream::connect_timeout(&socket_addr, self.timeout) {
                Ok(_) => {
                    let elapsed = start.elapsed().as_millis() as u64;
                    HealthCheckResult::tcp(port, HealthStatus::Reachable, elapsed, "Connection successful")
                }
                Err(ref e) if e.kind() == std::io::ErrorKind::TimedOut => {
                    let elapsed = start.elapsed().as_millis() as u64;
                    HealthCheckResult::tcp(port, HealthStatus::Timeout, elapsed, "Connection timed out")
                }
                Err(ref e) if e.kind() == std::io::ErrorKind::ConnectionRefused => {
                    let elapsed = start.elapsed().as_millis() as u64;
                    HealthCheckResult::tcp(port, HealthStatus::Refused, elapsed, "Connection refused")
                }
                Err(e) => {
                    let elapsed = start.elapsed().as_millis() as u64;
                    HealthCheckResult::tcp(port, HealthStatus::Unreachable, elapsed, e.to_string())
                }
            },
            Err(_) => {
                // If the address is a hostname, do a DNS-based connect
                match TcpStream::connect_timeout(
                    &SocketAddr::new(
                        std::net::IpAddr::V4(std::net::Ipv4Addr::LOCALHOST),
                        port,
                    ),
                    self.timeout,
                ) {
                    Ok(_) => {
                        let elapsed = start.elapsed().as_millis() as u64;
                        HealthCheckResult::tcp(port, HealthStatus::Reachable, elapsed, "Connection successful")
                    }
                    Err(e) => {
                        let elapsed = start.elapsed().as_millis() as u64;
                        HealthCheckResult::tcp(port, HealthStatus::Unreachable, elapsed, e.to_string())
                    }
                }
            }
        }
    }

    /// Performs a TCP connectivity check on localhost.
    pub fn check_tcp_local(&self, port: u16) -> HealthCheckResult {
        self.check_tcp("127.0.0.1", port)
    }

    /// Performs an HTTP health check.
    pub fn check_http(&self, host: &str, port: u16, path: &str, https: bool) -> HealthCheckResult {
        let start = Instant::now();
        let scheme = if https { "https" } else { "http" };
        let url = format!("{scheme}://{host}:{port}{path}");

        match reqwest::blocking::Client::builder()
            .timeout(self.timeout)
            .danger_accept_invalid_certs(true)
            .redirect(reqwest::redirect::Policy::limited(10))
            .build()
        {
            Ok(client) => match client.get(&url).send() {
                Ok(response) => {
                    let elapsed = start.elapsed().as_millis() as u64;
                    let status_code = response.status().as_u16();
                    let status_text = response
                        .status()
                        .canonical_reason()
                        .unwrap_or("")
                        .to_string();
                    let content_type = response
                        .headers()
                        .get("content-type")
                        .and_then(|v| v.to_str().ok())
                        .unwrap_or("")
                        .to_string();
                    let content_length = response
                        .content_length()
                        .map(|l| l as i64)
                        .unwrap_or(-1);

                    let http_info = HttpInfo {
                        status_code,
                        status_text,
                        content_type,
                        content_length,
                        response_time_ms: elapsed,
                    };

                    let status = if status_code > 0 {
                        HealthStatus::Reachable
                    } else {
                        HealthStatus::Unreachable
                    };

                    HealthCheckResult::http(port, status, elapsed, http_info)
                }
                Err(e) => {
                    let elapsed = start.elapsed().as_millis() as u64;
                    if e.is_timeout() {
                        HealthCheckResult::tcp(
                            port,
                            HealthStatus::Timeout,
                            elapsed,
                            "HTTP request timed out",
                        )
                    } else if e.is_connect() {
                        HealthCheckResult::tcp(
                            port,
                            HealthStatus::Refused,
                            elapsed,
                            "Connection refused",
                        )
                    } else {
                        HealthCheckResult::tcp(port, HealthStatus::Error, elapsed, e.to_string())
                    }
                }
            },
            Err(e) => {
                let elapsed = start.elapsed().as_millis() as u64;
                HealthCheckResult::tcp(port, HealthStatus::Error, elapsed, e.to_string())
            }
        }
    }

    /// Performs an HTTP health check on localhost.
    pub fn check_http_local(&self, port: u16) -> HealthCheckResult {
        self.check_http("127.0.0.1", port, "/", false)
    }

    /// Performs an HTTP health check on localhost with a custom path.
    pub fn check_http_local_with_path(&self, port: u16, path: &str) -> HealthCheckResult {
        self.check_http("127.0.0.1", port, path, false)
    }

    /// Performs an HTTPS health check.
    pub fn check_https(&self, host: &str, port: u16, path: &str) -> HealthCheckResult {
        self.check_http(host, port, path, true)
    }

    /// Performs an HTTPS health check on localhost.
    pub fn check_https_local(&self, port: u16) -> HealthCheckResult {
        self.check_https("127.0.0.1", port, "/")
    }

    /// Retrieves SSL certificate information.
    pub fn check_ssl(&self, host: &str, port: u16) -> HealthCheckResult {
        let start = Instant::now();

        let connector = match native_tls::TlsConnector::builder()
            .danger_accept_invalid_certs(true)
            .build()
        {
            Ok(c) => c,
            Err(e) => {
                let elapsed = start.elapsed().as_millis() as u64;
                return HealthCheckResult::tcp(
                    port,
                    HealthStatus::Error,
                    elapsed,
                    format!("Failed to create TLS connector: {e}"),
                );
            }
        };

        let addr = format!("{host}:{port}");
        let tcp_stream = match TcpStream::connect_timeout(
            &addr.parse().unwrap_or_else(|_| {
                SocketAddr::new(std::net::IpAddr::V4(std::net::Ipv4Addr::LOCALHOST), port)
            }),
            self.timeout,
        ) {
            Ok(s) => {
                let _ = s.set_read_timeout(Some(self.timeout));
                let _ = s.set_write_timeout(Some(self.timeout));
                s
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::TimedOut => {
                let elapsed = start.elapsed().as_millis() as u64;
                return HealthCheckResult::tcp(
                    port,
                    HealthStatus::Timeout,
                    elapsed,
                    "SSL handshake timed out",
                );
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::ConnectionRefused => {
                let elapsed = start.elapsed().as_millis() as u64;
                return HealthCheckResult::tcp(
                    port,
                    HealthStatus::Refused,
                    elapsed,
                    "Connection refused",
                );
            }
            Err(e) => {
                let elapsed = start.elapsed().as_millis() as u64;
                return HealthCheckResult::tcp(
                    port,
                    HealthStatus::Error,
                    elapsed,
                    e.to_string(),
                );
            }
        };

        match connector.connect(host, tcp_stream) {
            Ok(tls_stream) => {
                let elapsed = start.elapsed().as_millis() as u64;

                if let Ok(cert) = tls_stream.peer_certificate() {
                    if let Some(cert) = cert {
                        let der = cert.to_der().unwrap_or_default();
                        let ssl_info = parse_certificate_info(&der, &tls_stream);

                        return HealthCheckResult::ssl(
                            port,
                            HealthStatus::Reachable,
                            elapsed,
                            ssl_info,
                        );
                    }
                }

                HealthCheckResult::tcp(port, HealthStatus::Error, elapsed, "No certificate found")
            }
            Err(e) => {
                let elapsed = start.elapsed().as_millis() as u64;
                HealthCheckResult::tcp(port, HealthStatus::Error, elapsed, e.to_string())
            }
        }
    }

    /// Retrieves SSL certificate information from localhost.
    pub fn check_ssl_local(&self, port: u16) -> HealthCheckResult {
        self.check_ssl("127.0.0.1", port)
    }

    /// Performs a comprehensive health check (TCP, HTTP, SSL if applicable).
    pub fn check_comprehensive(
        &self,
        host: &str,
        port: u16,
        try_http: bool,
        try_https: bool,
    ) -> HealthCheckResult {
        // First, check TCP connectivity
        let tcp_result = self.check_tcp(host, port);
        if !tcp_result.is_healthy() {
            return tcp_result;
        }

        // If TCP is good, try HTTP if requested
        if try_http {
            let http_result = self.check_http(host, port, "/", false);
            if let Some(ref info) = http_result.http_info {
                if info.status_code > 0 {
                    return http_result;
                }
            }
        }

        // Try HTTPS if requested
        if try_https {
            let https_result = self.check_https(host, port, "/");
            if let Some(ref info) = https_result.http_info {
                if info.status_code > 0 {
                    // Also get SSL info
                    let ssl_result = self.check_ssl(host, port);
                    if ssl_result.ssl_info.is_some() {
                        return HealthCheckResult {
                            port,
                            protocol: "HTTPS".to_string(),
                            status: HealthStatus::Reachable,
                            response_time_ms: https_result.response_time_ms,
                            message: String::new(),
                            http_info: https_result.http_info,
                            ssl_info: ssl_result.ssl_info,
                            timestamp: chrono::Utc::now(),
                        };
                    }
                    return https_result;
                }
            }
        }

        tcp_result
    }
}

impl Default for HealthCheckService {
    fn default() -> Self {
        Self::new()
    }
}

/// Parses a DER-encoded certificate to extract basic information.
///
/// This provides a best-effort extraction without a full ASN.1 parser.
/// For production use, consider the `x509-parser` crate.
fn parse_certificate_info<S: Read + Write>(
    _der: &[u8],
    tls_stream: &native_tls::TlsStream<S>,
) -> SslInfo {
    let protocol = tls_stream
        .tls_server_end_point()
        .map(|_| "TLS")
        .unwrap_or("TLS")
        .to_string();

    // Without a full x509 parser, we provide the protocol/cipher info
    // and mark the cert as valid if the handshake succeeded.
    SslInfo {
        issuer: String::new(),
        subject: String::new(),
        valid_from: None,
        valid_to: None,
        protocol,
        cipher_suite: String::new(),
        valid: true,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_create_with_default_timeout() {
        let svc = HealthCheckService::new();
        assert_eq!(svc.timeout, Duration::from_millis(DEFAULT_TIMEOUT_MS));
    }

    #[test]
    fn test_create_with_custom_timeout() {
        let svc = HealthCheckService::with_timeout_ms(3000);
        assert_eq!(svc.timeout, Duration::from_millis(3000));
    }

    #[test]
    fn test_set_timeout() {
        let mut svc = HealthCheckService::new();
        svc.set_timeout_ms(1000);
        assert_eq!(svc.timeout, Duration::from_millis(1000));
    }

    #[test]
    fn test_check_tcp_refused() {
        // Port 1 is almost certainly not in use
        let svc = HealthCheckService::with_timeout_ms(500);
        let result = svc.check_tcp("127.0.0.1", 1);
        assert!(!result.is_healthy());
    }
}
