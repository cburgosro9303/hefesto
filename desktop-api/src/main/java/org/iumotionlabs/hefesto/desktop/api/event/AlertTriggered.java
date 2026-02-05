package org.iumotionlabs.hefesto.desktop.api.event;

import java.time.Instant;

public record AlertTriggered(String alertId, String message, String severity, Instant timestamp) implements AppEvent {
    public AlertTriggered(String alertId, String message, String severity) {
        this(alertId, message, severity, Instant.now());
    }
}
