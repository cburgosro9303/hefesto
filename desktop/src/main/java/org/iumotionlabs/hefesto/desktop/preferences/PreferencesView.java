package org.iumotionlabs.hefesto.desktop.preferences;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

public class PreferencesView extends VBox {

    public PreferencesView() {
        var viewModel = new PreferencesViewModel();
        var i18n = I18nService.getInstance();

        getStyleClass().add("preferences-view");
        setSpacing(20);
        setPadding(new Insets(20));

        var title = new Label(i18n.t("prefs.title"));
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Theme section
        var themeLabel = new Label(i18n.t("prefs.theme"));
        themeLabel.setStyle("-fx-font-weight: bold;");
        var darkModeToggle = new CheckBox(i18n.t("prefs.theme.dark"));
        darkModeToggle.selectedProperty().bindBidirectional(viewModel.darkModeProperty());
        var themeSection = new VBox(6, themeLabel, darkModeToggle);

        // Language section
        var langLabel = new Label(i18n.t("prefs.language"));
        langLabel.setStyle("-fx-font-weight: bold;");
        var langCombo = new ComboBox<String>();
        langCombo.getItems().addAll("en", "es");
        langCombo.valueProperty().bindBidirectional(viewModel.languageProperty());
        var langSection = new VBox(6, langLabel, langCombo);

        // Auto-refresh section
        var refreshLabel = new Label(i18n.t("prefs.auto.refresh"));
        refreshLabel.setStyle("-fx-font-weight: bold;");
        var autoRefreshToggle = new CheckBox(i18n.t("prefs.auto.refresh"));
        autoRefreshToggle.selectedProperty().bindBidirectional(viewModel.autoRefreshProperty());

        var intervalLabel = new Label(i18n.t("prefs.auto.refresh.interval"));
        var intervalSpinner = new Spinner<Integer>(1, 60, viewModel.refreshIntervalProperty().get());
        viewModel.refreshIntervalProperty().bind(intervalSpinner.valueProperty());
        var refreshSection = new VBox(6, refreshLabel, autoRefreshToggle, intervalLabel, intervalSpinner);

        getChildren().addAll(title, new Separator(), themeSection, new Separator(), langSection, new Separator(), refreshSection);
    }
}
