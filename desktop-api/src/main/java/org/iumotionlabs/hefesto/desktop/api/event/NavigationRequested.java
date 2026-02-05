package org.iumotionlabs.hefesto.desktop.api.event;

import java.time.Instant;

public record NavigationRequested(String targetViewId, Instant timestamp) implements AppEvent {
    public NavigationRequested(String targetViewId) {
        this(targetViewId, Instant.now());
    }
}
