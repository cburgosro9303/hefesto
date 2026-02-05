package org.iumotionlabs.hefesto.desktop.api.event;

import java.time.Instant;

public record ThemeChanged(String themeName, Instant timestamp) implements AppEvent {
    public ThemeChanged(String themeName) {
        this(themeName, Instant.now());
    }
}
