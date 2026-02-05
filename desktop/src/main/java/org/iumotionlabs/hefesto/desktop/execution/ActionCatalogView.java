package org.iumotionlabs.hefesto.desktop.execution;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.controls.SearchField;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

public class ActionCatalogView extends VBox {

    public ActionCatalogView(ActionCatalogViewModel viewModel) {
        getStyleClass().add("action-catalog-view");
        setSpacing(10);
        setPadding(new Insets(15));

        var title = new Label(I18nService.getInstance().t("execution.action.catalog"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var searchField = new SearchField();
        searchField.debouncedTextProperty().addListener((_, _, text) -> viewModel.searchQueryProperty().set(text));

        var actionList = new ListView<org.iumotionlabs.hefesto.desktop.api.action.ActionDescriptor>();
        actionList.setItems(viewModel.getFilteredActions());
        actionList.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(org.iumotionlabs.hefesto.desktop.api.action.ActionDescriptor item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    var label = new Label(I18nService.getInstance().t(item.labelKey()));
                    label.setStyle("-fx-font-weight: bold;");
                    var desc = new Label(I18nService.getInstance().t(item.descriptionKey()));
                    desc.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
                    var categoryBadge = new Label(item.category());
                    categoryBadge.getStyleClass().add("status-badge");
                    categoryBadge.getStyleClass().add("status-badge-info");

                    var left = new VBox(2, label, desc);
                    var spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    setGraphic(new HBox(10, left, spacer, categoryBadge));
                }
            }
        });

        VBox.setVgrow(actionList, Priority.ALWAYS);
        getChildren().addAll(title, searchField, actionList);
    }
}
