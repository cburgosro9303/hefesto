package org.iumotionlabs.hefesto.desktop.preferences;

import javafx.beans.property.*;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;
import org.iumotionlabs.hefesto.desktop.theme.ThemeManager;

import java.util.Locale;

public class PreferencesViewModel extends BaseViewModel {

    private final BooleanProperty darkMode = new SimpleBooleanProperty();
    private final StringProperty language = new SimpleStringProperty("en");
    private final BooleanProperty autoRefresh = new SimpleBooleanProperty(true);
    private final IntegerProperty refreshInterval = new SimpleIntegerProperty(5);

    public PreferencesViewModel() {
        var prefs = PreferencesService.getInstance();
        darkMode.set(prefs.getBoolean("theme.dark", false));
        language.set(prefs.getString("language", "en"));
        autoRefresh.set(prefs.getBoolean("auto.refresh", true));
        refreshInterval.set(prefs.getInt("refresh.interval", 5));

        darkMode.addListener((_, _, dark) -> {
            prefs.put("theme.dark", dark);
            ThemeManager.getInstance().setTheme(dark ? ThemeManager.DARK : ThemeManager.LIGHT);
        });

        language.addListener((_, _, lang) -> {
            prefs.put("language", lang);
            I18nService.getInstance().setLocale(Locale.forLanguageTag(lang));
        });

        autoRefresh.addListener((_, _, val) -> prefs.put("auto.refresh", val));
        refreshInterval.addListener((_, _, val) -> prefs.put("refresh.interval", val.intValue()));
    }

    public BooleanProperty darkModeProperty() { return darkMode; }
    public StringProperty languageProperty() { return language; }
    public BooleanProperty autoRefreshProperty() { return autoRefresh; }
    public IntegerProperty refreshIntervalProperty() { return refreshInterval; }
}
