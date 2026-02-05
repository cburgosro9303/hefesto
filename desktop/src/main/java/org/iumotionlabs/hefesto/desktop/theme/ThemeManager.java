package org.iumotionlabs.hefesto.desktop.theme;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import org.iumotionlabs.hefesto.desktop.event.EventBus;
import org.iumotionlabs.hefesto.desktop.api.event.ThemeChanged;

import java.util.List;
import java.util.Objects;

public final class ThemeManager {

    public static final Theme LIGHT = new Theme("light", List.of(
        "/css/base.css",
        "/css/theme-light.css"
    ));
    public static final Theme DARK = new Theme("dark", List.of(
        "/css/base.css",
        "/css/theme-dark.css"
    ));

    private static final ThemeManager INSTANCE = new ThemeManager();

    private final ObjectProperty<Theme> currentTheme = new SimpleObjectProperty<>(LIGHT);
    private Scene scene;

    private ThemeManager() {}

    public static ThemeManager getInstance() { return INSTANCE; }

    public ObjectProperty<Theme> currentThemeProperty() { return currentTheme; }

    public Theme getCurrentTheme() { return currentTheme.get(); }

    public void apply(Scene scene) {
        this.scene = scene;
        applyTheme(currentTheme.get());
        currentTheme.addListener((_, _, newTheme) -> applyTheme(newTheme));
    }

    public void toggleTheme() {
        setTheme(getCurrentTheme() == LIGHT ? DARK : LIGHT);
    }

    public void setTheme(Theme theme) {
        currentTheme.set(theme);
        EventBus.getInstance().publish(new ThemeChanged(theme.name()));
    }

    private void applyTheme(Theme theme) {
        if (scene == null) return;
        scene.getStylesheets().clear();
        for (var path : theme.stylesheetPaths()) {
            var url = getClass().getResource(path);
            if (url != null) {
                scene.getStylesheets().add(url.toExternalForm());
            }
        }
    }
}
