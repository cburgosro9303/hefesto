package org.iumotionlabs.hefesto.desktop.preferences;

import org.iumotionlabs.hefesto.desktop.api.preferences.PreferencesAccessor;

import java.util.Optional;
import java.util.prefs.Preferences;

public final class PreferencesService implements PreferencesAccessor {

    private static final PreferencesService INSTANCE = new PreferencesService();
    private final Preferences prefs = Preferences.userNodeForPackage(PreferencesService.class);

    private PreferencesService() {}

    public static PreferencesService getInstance() { return INSTANCE; }

    @Override
    public Optional<String> getString(String key) {
        return Optional.ofNullable(prefs.get(key, null));
    }

    @Override
    public String getString(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    @Override
    public Optional<Integer> getInt(String key) {
        var val = prefs.get(key, null);
        return val != null ? Optional.of(Integer.parseInt(val)) : Optional.empty();
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
        var val = prefs.get(key, null);
        return val != null ? Optional.of(Boolean.parseBoolean(val)) : Optional.empty();
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    @Override
    public void put(String key, String value) {
        prefs.put(key, value);
    }

    @Override
    public void put(String key, int value) {
        prefs.putInt(key, value);
    }

    @Override
    public void put(String key, boolean value) {
        prefs.putBoolean(key, value);
    }

    @Override
    public void remove(String key) {
        prefs.remove(key);
    }
}
