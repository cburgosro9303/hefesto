package org.iumotionlabs.hefesto.core.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.time.Duration;

/**
 * Central configuration holder for Hefesto.
 * Loads configuration from hefesto.yml at startup.
 */
public final class HefestoConfig {

    private static final HefestoConfig INSTANCE = loadConfig();

    // Display settings
    private final int maxTextWidth;
    private final int maxProcessNameWidth;
    private final int maxCommandWidth;
    private final int maxServiceWidth;

    // PortInfo settings
    private final int healthCheckTimeoutMs;
    private final int sslTimeoutMs;
    private final int maxPortRange;

    // ProcWatch settings
    private final int defaultIntervalMs;
    private final int defaultTopLimit;
    private final int alertHistorySeconds;

    private HefestoConfig(ConfigData data) {
        // Display
        this.maxTextWidth = data.display != null ? data.display.maxTextWidth : 256;
        this.maxProcessNameWidth = data.display != null ? data.display.maxProcessNameWidth : 256;
        this.maxCommandWidth = data.display != null ? data.display.maxCommandWidth : 256;
        this.maxServiceWidth = data.display != null ? data.display.maxServiceWidth : 256;

        // PortInfo
        this.healthCheckTimeoutMs = data.portinfo != null ? data.portinfo.healthCheckTimeoutMs : 5000;
        this.sslTimeoutMs = data.portinfo != null ? data.portinfo.sslTimeoutMs : 10000;
        this.maxPortRange = data.portinfo != null ? data.portinfo.maxPortRange : 1000;

        // ProcWatch
        this.defaultIntervalMs = data.procwatch != null ? data.procwatch.defaultIntervalMs : 1000;
        this.defaultTopLimit = data.procwatch != null ? data.procwatch.defaultTopLimit : 10;
        this.alertHistorySeconds = data.procwatch != null ? data.procwatch.alertHistorySeconds : 600;
    }

    /**
     * Returns the singleton instance.
     */
    public static HefestoConfig get() {
        return INSTANCE;
    }

    // Display getters

    /**
     * Maximum text width before truncation. Returns -1 if truncation is disabled.
     */
    public int maxTextWidth() {
        return maxTextWidth;
    }

    /**
     * Maximum width for process names in tables.
     */
    public int maxProcessNameWidth() {
        return maxProcessNameWidth;
    }

    /**
     * Maximum width for command lines in tables.
     */
    public int maxCommandWidth() {
        return maxCommandWidth;
    }

    /**
     * Maximum width for service names in tables.
     */
    public int maxServiceWidth() {
        return maxServiceWidth;
    }

    // PortInfo getters

    /**
     * Health check timeout in milliseconds.
     */
    public int healthCheckTimeoutMs() {
        return healthCheckTimeoutMs;
    }

    /**
     * SSL certificate check timeout in milliseconds.
     */
    public int sslTimeoutMs() {
        return sslTimeoutMs;
    }

    /**
     * Maximum port range for scans.
     */
    public int maxPortRange() {
        return maxPortRange;
    }

    // ProcWatch getters

    /**
     * Default sampling interval.
     */
    public Duration defaultInterval() {
        return Duration.ofMillis(defaultIntervalMs);
    }

    /**
     * Default top limit.
     */
    public int defaultTopLimit() {
        return defaultTopLimit;
    }

    /**
     * Alert history duration.
     */
    public Duration alertHistoryDuration() {
        return Duration.ofSeconds(alertHistorySeconds);
    }

    // Utility methods

    /**
     * Truncates text to the configured max width.
     * Returns the original text if truncation is disabled (maxTextWidth = -1)
     * or if the text is shorter than the limit.
     */
    public String truncate(String text) {
        return truncate(text, maxTextWidth);
    }

    /**
     * Truncates text to the specified max width.
     * Returns the original text if maxWidth is -1 (disabled)
     * or if the text is shorter than the limit.
     */
    public String truncate(String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth < 0 || text.length() <= maxWidth) return text;
        if (maxWidth <= 3) return text.substring(0, maxWidth);
        return text.substring(0, maxWidth - 3) + "...";
    }

    /**
     * Truncates process name according to configuration.
     */
    public String truncateProcessName(String name) {
        return truncate(name, maxProcessNameWidth);
    }

    /**
     * Truncates command line according to configuration.
     */
    public String truncateCommand(String command) {
        return truncate(command, maxCommandWidth);
    }

    /**
     * Truncates service name according to configuration.
     */
    public String truncateService(String service) {
        return truncate(service, maxServiceWidth);
    }

    private static HefestoConfig loadConfig() {
        try (InputStream is = HefestoConfig.class.getResourceAsStream("/hefesto.yml")) {
            if (is == null) {
                System.err.println("Warning: hefesto.yml not found, using defaults");
                return new HefestoConfig(new ConfigData());
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            ConfigData data = mapper.readValue(is, ConfigData.class);
            return new HefestoConfig(data);
        } catch (Exception e) {
            System.err.println("Warning: Error loading hefesto.yml: " + e.getMessage() + ", using defaults");
            return new HefestoConfig(new ConfigData());
        }
    }

    // Internal data classes for YAML mapping

    static class ConfigData {
        public DisplayConfig display;
        public PortInfoConfig portinfo;
        public ProcWatchConfig procwatch;
    }

    static class DisplayConfig {
        public int maxTextWidth = 256;
        public int maxProcessNameWidth = 256;
        public int maxCommandWidth = 256;
        public int maxServiceWidth = 256;
    }

    static class PortInfoConfig {
        public int healthCheckTimeoutMs = 5000;
        public int sslTimeoutMs = 10000;
        public int maxPortRange = 1000;
    }

    static class ProcWatchConfig {
        public int defaultIntervalMs = 1000;
        public int defaultTopLimit = 10;
        public int alertHistorySeconds = 600;
    }
}
