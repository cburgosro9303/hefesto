package org.iumotionlabs.hefesto.feature.portinfo.model;

/**
 * Record representing a security flag or warning.
 */
public record SecurityFlag(
    Severity severity,
    Category category,
    String title,
    String description,
    String recommendation,
    PortBinding relatedBinding
) {
    /**
     * Severity levels for security findings.
     */
    public enum Severity {
        CRITICAL("Critical", 4),
        HIGH("High", 3),
        WARNING("Warning", 2),
        INFO("Info", 1);

        private final String displayName;
        private final int level;

        Severity(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
        }

        public String displayName() {
            return displayName;
        }

        public int level() {
            return level;
        }
    }

    /**
     * Categories of security findings.
     */
    public enum Category {
        NETWORK_EXPOSURE("Network Exposure"),
        PRIVILEGE("Privilege"),
        DEBUG("Debug/Development"),
        DATABASE("Database"),
        CONFIGURATION("Configuration"),
        PROTOCOL("Protocol");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * Creates a critical security flag.
     */
    public static SecurityFlag critical(Category category, String title, String description,
                                        String recommendation, PortBinding binding) {
        return new SecurityFlag(Severity.CRITICAL, category, title, description, recommendation, binding);
    }

    /**
     * Creates a high severity security flag.
     */
    public static SecurityFlag high(Category category, String title, String description,
                                    String recommendation, PortBinding binding) {
        return new SecurityFlag(Severity.HIGH, category, title, description, recommendation, binding);
    }

    /**
     * Creates a warning security flag.
     */
    public static SecurityFlag warning(Category category, String title, String description,
                                       String recommendation, PortBinding binding) {
        return new SecurityFlag(Severity.WARNING, category, title, description, recommendation, binding);
    }

    /**
     * Creates an info security flag.
     */
    public static SecurityFlag info(Category category, String title, String description,
                                    String recommendation, PortBinding binding) {
        return new SecurityFlag(Severity.INFO, category, title, description, recommendation, binding);
    }

    /**
     * Returns a formatted summary line.
     */
    public String toSummaryLine() {
        return ":%d %s [%s] - %s".formatted(
            relatedBinding != null ? relatedBinding.port() : 0,
            relatedBinding != null ? relatedBinding.processName() : "",
            title,
            description
        );
    }
}
