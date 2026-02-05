package org.iumotionlabs.hefesto.desktop.api.feature;

import org.iumotionlabs.hefesto.desktop.api.preferences.PreferencesAccessor;

public interface FeatureContext {

    void publishEvent(Object event);

    String translate(String key, Object... args);

    PreferencesAccessor preferences();

    <T> T getService(Class<T> serviceType);
}
