package org.iumotionlabs.hefesto.desktop.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TextField;
import org.iumotionlabs.hefesto.desktop.concurrency.FxScheduler;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

public class SearchField extends TextField {

    private final StringProperty debouncedText = new SimpleStringProperty("");

    public SearchField() {
        this(300);
    }

    public SearchField(long debounceMs) {
        getStyleClass().add("search-field");
        setPromptText(I18nService.getInstance().t("common.search"));

        var debounced = FxScheduler.debounce(() -> debouncedText.set(getText()), debounceMs);
        textProperty().addListener((_, _, _) -> debounced.run());
    }

    public StringProperty debouncedTextProperty() { return debouncedText; }
    public String getDebouncedText() { return debouncedText.get(); }
}
