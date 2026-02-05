package org.iumotionlabs.hefesto.feature.portinfo.model;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Record representing the result of a health check.
 */
public record HealthCheckResult(
    int port,
    String protocol,
    HealthStatus status,
    long responseTimeMs,
    String message,
    HttpInfo httpInfo,
    SslInfo sslInfo,
    LocalDateTime timestamp
) {
    /**
     * Health check status.
     */
    public enum HealthStatus {
        REACHABLE("Reachable", true),
        UNREACHABLE("Unreachable", false),
        TIMEOUT("Timeout", false),
        REFUSED("Connection Refused", false),
        ERROR("Error", false);

        private final String displayName;
        private final boolean healthy;

        HealthStatus(String displayName, boolean healthy) {
            this.displayName = displayName;
            this.healthy = healthy;
        }

        public String displayName() {
            return displayName;
        }

        public boolean isHealthy() {
            return healthy;
        }
    }

    /**
     * HTTP-specific health information.
     */
    public record HttpInfo(
        int statusCode,
        String statusText,
        String contentType,
        long contentLength,
        long responseTimeMs
    ) {
        /**
         * Checks if HTTP status is successful (2xx).
         */
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        /**
         * Returns formatted status: "200 OK"
         */
        public String statusFormatted() {
            return "%d %s".formatted(statusCode, statusText);
        }

        /**
         * Returns content length in human-readable format.
         */
        public String contentLengthFormatted() {
            if (contentLength < 0) {
                return "unknown";
            } else if (contentLength < 1024) {
                return contentLength + " bytes";
            } else if (contentLength < 1024 * 1024) {
                return "%.1f KB".formatted(contentLength / 1024.0);
            } else {
                return "%.2f MB".formatted(contentLength / (1024.0 * 1024.0));
            }
        }
    }

    /**
     * SSL certificate information.
     */
    public record SslInfo(
        String issuer,
        String subject,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String protocol,
        String cipherSuite,
        boolean valid
    ) {
        /**
         * Returns days until expiration.
         */
        public long daysUntilExpiry() {
            if (validTo == null) {
                return -1;
            }
            return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), validTo);
        }

        /**
         * Checks if certificate is expired.
         */
        public boolean isExpired() {
            return validTo != null && LocalDateTime.now().isAfter(validTo);
        }

        /**
         * Checks if certificate expires soon (within 30 days).
         */
        public boolean expiresSoon() {
            return daysUntilExpiry() >= 0 && daysUntilExpiry() <= 30;
        }
    }

    /**
     * Creates a TCP-only health check result.
     */
    public static HealthCheckResult tcp(int port, HealthStatus status, long responseTimeMs, String message) {
        return new HealthCheckResult(port, "TCP", status, responseTimeMs, message, null, null, LocalDateTime.now());
    }

    /**
     * Creates an HTTP health check result.
     */
    public static HealthCheckResult http(int port, HealthStatus status, long responseTimeMs, HttpInfo httpInfo) {
        return new HealthCheckResult(port, "HTTP", status, responseTimeMs, "", httpInfo, null, LocalDateTime.now());
    }

    /**
     * Creates an SSL health check result.
     */
    public static HealthCheckResult ssl(int port, HealthStatus status, long responseTimeMs, SslInfo sslInfo) {
        return new HealthCheckResult(port, "SSL", status, responseTimeMs, "", null, sslInfo, LocalDateTime.now());
    }

    /**
     * Returns optional HTTP info.
     */
    public Optional<HttpInfo> httpInfoOpt() {
        return Optional.ofNullable(httpInfo);
    }

    /**
     * Returns optional SSL info.
     */
    public Optional<SslInfo> sslInfoOpt() {
        return Optional.ofNullable(sslInfo);
    }

    /**
     * Checks if the health check was successful.
     */
    public boolean isHealthy() {
        return status.isHealthy();
    }
}
