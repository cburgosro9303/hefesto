package org.iumotionlabs.hefesto.desktop.observability;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.controls.SearchField;

public class LogViewerView extends VBox {

    public LogViewerView(LogViewerViewModel viewModel) {
        getStyleClass().add("log-viewer");
        setSpacing(8);
        setPadding(new Insets(10));

        var levelCombo = new ComboBox<String>();
        levelCombo.getItems().addAll("ALL", "DEBUG", "INFO", "WARN", "ERROR");
        levelCombo.setValue("ALL");
        levelCombo.valueProperty().bindBidirectional(viewModel.levelFilterProperty());

        var searchField = new SearchField();
        searchField.debouncedTextProperty().addListener((_, _, text) -> viewModel.searchFilterProperty().set(text));
        HBox.setHgrow(searchField, Priority.ALWAYS);

        var clearBtn = new Button("Clear");
        clearBtn.setOnAction(_ -> viewModel.clear());

        var toolbar = new HBox(8, levelCombo, searchField, clearBtn);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        var logTable = new TableView<LogViewerViewModel.LogEntry>();
        logTable.setItems(viewModel.getFilteredEntries());

        var timeCol = new TableColumn<LogViewerViewModel.LogEntry, String>("Time");
        timeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().timestamp()));
        timeCol.setPrefWidth(100);

        var levelCol = new TableColumn<LogViewerViewModel.LogEntry, String>("Level");
        levelCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().level()));
        levelCol.setPrefWidth(60);

        var msgCol = new TableColumn<LogViewerViewModel.LogEntry, String>("Message");
        msgCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().message()));

        logTable.getColumns().addAll(timeCol, levelCol, msgCol);
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_LAST_COLUMN);
        VBox.setVgrow(logTable, Priority.ALWAYS);

        getChildren().addAll(toolbar, logTable);
    }
}
