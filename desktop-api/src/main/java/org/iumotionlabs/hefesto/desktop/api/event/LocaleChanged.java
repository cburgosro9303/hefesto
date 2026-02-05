package org.iumotionlabs.hefesto.desktop.api.event;

import java.time.Instant;
import java.util.Locale;

public record LocaleChanged(Locale locale, Instant timestamp) implements AppEvent {
    public LocaleChanged(Locale locale) {
        this(locale, Instant.now());
    }
}
