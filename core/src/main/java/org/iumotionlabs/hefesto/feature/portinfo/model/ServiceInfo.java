package org.iumotionlabs.hefesto.feature.portinfo.model;

/**
 * Record representing information about a known service mapped to a port.
 */
public record ServiceInfo(
    String name,
    String description,
    ServiceCategory category
) {
    /**
     * Categories of services.
     */
    public enum ServiceCategory {
        DATABASE("Database"),
        WEB("Web Server"),
        MESSAGING("Messaging"),
        CACHE("Cache"),
        SEARCH("Search"),
        DEV("Development"),
        INFRA("Infrastructure"),
        MONITORING("Monitoring"),
        SECURITY("Security"),
        OTHER("Other");

        private final String displayName;

        ServiceCategory(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * Returns a formatted string representation for display.
     */
    public String toDisplayString() {
        return "[%s - %s]".formatted(name, description);
    }

    /**
     * Returns a short tag format.
     */
    public String toTag() {
        return "[%s]".formatted(name);
    }
}
