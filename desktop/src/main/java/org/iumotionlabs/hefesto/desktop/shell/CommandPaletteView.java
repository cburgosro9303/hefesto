package org.iumotionlabs.hefesto.desktop.shell;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

public class CommandPaletteView extends VBox {

    private final TextField searchField = new TextField();
    private final ListView<CommandPaletteViewModel.PaletteEntry> resultsList = new ListView<>();
    private Runnable onClose;

    public CommandPaletteView(CommandPaletteViewModel viewModel) {
        getStyleClass().add("command-palette");
        setMaxWidth(500);
        setMaxHeight(400);
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(10));
        setSpacing(8);

        searchField.setPromptText("Type to search actions and views...");
        searchField.getStyleClass().add("command-palette-search");
        searchField.textProperty().bindBidirectional(viewModel.searchQueryProperty());

        resultsList.setItems(viewModel.getFilteredEntries());
        resultsList.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(CommandPaletteViewModel.PaletteEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    var label = new Label(item.label());
                    var category = new Label(item.category());
                    category.getStyleClass().add("palette-entry-category");
                    var spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    setGraphic(new HBox(8, label, spacer, category));
                }
            }
        });

        resultsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) executeSelected();
        });

        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && onClose != null) onClose.run();
            if (e.getCode() == KeyCode.ENTER) executeSelected();
        });

        VBox.setVgrow(resultsList, Priority.ALWAYS);
        getChildren().addAll(searchField, resultsList);
    }

    public void setOnClose(Runnable handler) { this.onClose = handler; }

    public void focus() {
        searchField.requestFocus();
        searchField.clear();
    }

    private void executeSelected() {
        var selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selected.action().run();
            if (onClose != null) onClose.run();
        }
    }
}
