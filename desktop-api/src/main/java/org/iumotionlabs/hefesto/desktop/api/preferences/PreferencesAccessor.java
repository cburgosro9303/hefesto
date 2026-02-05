package org.iumotionlabs.hefesto.desktop.api.preferences;

import java.util.Optional;

public interface PreferencesAccessor {

    Optional<String> getString(String key);

    String getString(String key, String defaultValue);

    Optional<Integer> getInt(String key);

    int getInt(String key, int defaultValue);

    Optional<Boolean> getBoolean(String key);

    boolean getBoolean(String key, boolean defaultValue);

    void put(String key, String value);

    void put(String key, int value);

    void put(String key, boolean value);

    void remove(String key);
}
