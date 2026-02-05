package org.iumotionlabs.hefesto.feature.portinfo.service;

import org.iumotionlabs.hefesto.feature.portinfo.model.HealthCheckResult;
import org.iumotionlabs.hefesto.feature.portinfo.model.HealthCheckResult.*;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Service for performing health checks on ports (TCP, HTTP, SSL).
 */
public final class HealthCheckService {

    private static final int DEFAULT_TIMEOUT_MS = 5000;

    private int timeoutMs;

    public HealthCheckService() {
        this.timeoutMs = DEFAULT_TIMEOUT_MS;
    }

    public HealthCheckService(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Sets the timeout for health checks.
     */
    public void setTimeout(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Performs a TCP connectivity check.
     */
    public HealthCheckResult checkTcp(String host, int port) {
        long startTime = System.currentTimeMillis();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long responseTime = System.currentTimeMillis() - startTime;

            return HealthCheckResult.tcp(port, HealthStatus.REACHABLE, responseTime, "Connection successful");
        } catch (SocketTimeoutException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.TIMEOUT, responseTime, "Connection timed out");
        } catch (ConnectException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.REFUSED, responseTime, "Connection refused");
        } catch (IOException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.UNREACHABLE, responseTime, e.getMessage());
        }
    }

    /**
     * Performs a TCP connectivity check on localhost.
     */
    public HealthCheckResult checkTcp(int port) {
        return checkTcp("127.0.0.1", port);
    }

    /**
     * Performs an HTTP health check.
     */
    public HealthCheckResult checkHttp(String host, int port, String path, boolean https) {
        long startTime = System.currentTimeMillis();

        try {
            String protocol = https ? "https" : "http";
            URL url = new URI(protocol + "://" + host + ":" + port + path).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);

            // For HTTPS, accept all certificates for health check purposes
            if (conn instanceof HttpsURLConnection httpsConn) {
                httpsConn.setSSLSocketFactory(createTrustAllSslSocketFactory());
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }

            int statusCode = conn.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            String statusText = conn.getResponseMessage();
            String contentType = conn.getContentType();
            long contentLength = conn.getContentLengthLong();

            HttpInfo httpInfo = new HttpInfo(statusCode, statusText, contentType, contentLength, responseTime);
            HealthStatus status = statusCode > 0 ? HealthStatus.REACHABLE : HealthStatus.UNREACHABLE;

            return HealthCheckResult.http(port, status, responseTime, httpInfo);
        } catch (SocketTimeoutException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.TIMEOUT, responseTime, "HTTP request timed out");
        } catch (ConnectException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.REFUSED, responseTime, "Connection refused");
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.ERROR, responseTime, e.getMessage());
        }
    }

    /**
     * Performs an HTTP health check on localhost.
     */
    public HealthCheckResult checkHttp(int port) {
        return checkHttp("127.0.0.1", port, "/", false);
    }

    /**
     * Performs an HTTP health check on localhost with custom path.
     */
    public HealthCheckResult checkHttp(int port, String path) {
        return checkHttp("127.0.0.1", port, path, false);
    }

    /**
     * Performs an HTTPS health check.
     */
    public HealthCheckResult checkHttps(String host, int port, String path) {
        return checkHttp(host, port, path, true);
    }

    /**
     * Performs an HTTPS health check on localhost.
     */
    public HealthCheckResult checkHttps(int port) {
        return checkHttps("127.0.0.1", port, "/");
    }

    /**
     * Retrieves SSL certificate information.
     */
    public HealthCheckResult checkSsl(String host, int port) {
        long startTime = System.currentTimeMillis();

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, createTrustAllManagers(), new java.security.SecureRandom());

            SSLSocketFactory factory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
                socket.startHandshake();

                SSLSession session = socket.getSession();
                Certificate[] certificates = session.getPeerCertificates();

                if (certificates.length > 0 && certificates[0] instanceof X509Certificate cert) {
                    long responseTime = System.currentTimeMillis() - startTime;

                    String issuer = cert.getIssuerX500Principal().getName();
                    String subject = cert.getSubjectX500Principal().getName();
                    LocalDateTime validFrom = LocalDateTime.ofInstant(
                        cert.getNotBefore().toInstant(), ZoneId.systemDefault()
                    );
                    LocalDateTime validTo = LocalDateTime.ofInstant(
                        cert.getNotAfter().toInstant(), ZoneId.systemDefault()
                    );
                    String protocol = session.getProtocol();
                    String cipherSuite = session.getCipherSuite();

                    // Check if currently valid
                    boolean valid = !LocalDateTime.now().isBefore(validFrom) &&
                                   !LocalDateTime.now().isAfter(validTo);

                    SslInfo sslInfo = new SslInfo(issuer, subject, validFrom, validTo, protocol, cipherSuite, valid);

                    return HealthCheckResult.ssl(port, HealthStatus.REACHABLE, responseTime, sslInfo);
                }
            }

            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.ERROR, responseTime, "No certificate found");
        } catch (SocketTimeoutException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.TIMEOUT, responseTime, "SSL handshake timed out");
        } catch (ConnectException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.REFUSED, responseTime, "Connection refused");
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.tcp(port, HealthStatus.ERROR, responseTime, e.getMessage());
        }
    }

    /**
     * Retrieves SSL certificate information from localhost.
     */
    public HealthCheckResult checkSsl(int port) {
        return checkSsl("127.0.0.1", port);
    }

    /**
     * Performs a comprehensive health check (TCP, HTTP, SSL if applicable).
     */
    public HealthCheckResult checkComprehensive(String host, int port, boolean tryHttp, boolean tryHttps) {
        // First, check TCP connectivity
        HealthCheckResult tcpResult = checkTcp(host, port);
        if (!tcpResult.isHealthy()) {
            return tcpResult;
        }

        // If TCP is good, try HTTP if requested
        if (tryHttp) {
            HealthCheckResult httpResult = checkHttp(host, port, "/", false);
            if (httpResult.httpInfo() != null && httpResult.httpInfo().statusCode() > 0) {
                return httpResult;
            }
        }

        // Try HTTPS if requested
        if (tryHttps) {
            HealthCheckResult httpsResult = checkHttps(host, port, "/");
            if (httpsResult.httpInfo() != null && httpsResult.httpInfo().statusCode() > 0) {
                // Also get SSL info
                HealthCheckResult sslResult = checkSsl(host, port);
                if (sslResult.sslInfo() != null) {
                    return new HealthCheckResult(
                        port, "HTTPS", HealthStatus.REACHABLE,
                        httpsResult.responseTimeMs(), "",
                        httpsResult.httpInfo(), sslResult.sslInfo(),
                        LocalDateTime.now()
                    );
                }
                return httpsResult;
            }
        }

        return tcpResult;
    }

    private SSLSocketFactory createTrustAllSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, createTrustAllManagers(), new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL socket factory", e);
        }
    }

    private TrustManager[] createTrustAllManagers() {
        return new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };
    }
}
