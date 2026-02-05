package org.iumotionlabs.hefesto.desktop.i18n;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.iumotionlabs.hefesto.desktop.event.EventBus;
import org.iumotionlabs.hefesto.desktop.api.event.LocaleChanged;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class I18nService {

    private static final I18nService INSTANCE = new I18nService();
    private static final String BUNDLE_BASE = "i18n.messages";

    private final ObjectProperty<Locale> currentLocale = new SimpleObjectProperty<>(Locale.ENGLISH);
    private ResourceBundle bundle;

    private I18nService() {}

    public static I18nService getInstance() { return INSTANCE; }

    public void initialize() {
        loadBundle(currentLocale.get());
        currentLocale.addListener((_, _, newLocale) -> {
            loadBundle(newLocale);
            EventBus.getInstance().publish(new LocaleChanged(newLocale));
        });
    }

    public ObjectProperty<Locale> localeProperty() { return currentLocale; }

    public Locale getCurrentLocale() { return currentLocale.get(); }

    public void setLocale(Locale locale) { currentLocale.set(locale); }

    public String translate(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            if (args.length > 0) {
                return MessageFormat.format(pattern, args);
            }
            return pattern;
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public String t(String key, Object... args) {
        return translate(key, args);
    }

    private void loadBundle(Locale locale) {
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale);
    }
}
