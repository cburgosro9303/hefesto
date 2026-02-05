package org.iumotionlabs.hefesto.desktop.feature.procwatch.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.dashboard.WidgetContainer;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;
import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;

import java.util.Locale;

public class TopProcessesWidget extends VBox implements WidgetContainer.Refreshable {

    private final TopProcessesWidgetViewModel viewModel = new TopProcessesWidgetViewModel();

    public TopProcessesWidget() {
        var i18n = I18nService.getInstance();
        setSpacing(6);
        setPadding(new Insets(4));

        var playBtn = new Button("\u25B6"); // ▶
        playBtn.setOnAction(_ -> {
            if (viewModel.playingProperty().get()) {
                viewModel.stopLiveUpdates();
                playBtn.setText("\u25B6");
            } else {
                viewModel.startLiveUpdates();
                playBtn.setText("\u23F8"); // ⏸
            }
        });

        var toolbar = new HBox(8, playBtn);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        var table = new TableView<ProcessSample>();
        table.setItems(viewModel.getTopProcesses());

        var pidCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.pid"));
        pidCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().pid())));
        pidCol.setPrefWidth(60);

        var nameCol = new TableColumn<ProcessSample, String>("Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        nameCol.setPrefWidth(100);

        var cpuCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.cpu"));
        cpuCol.setCellValueFactory(d -> new SimpleStringProperty(
            String.format(Locale.US, "%.1f", d.getValue().cpu().percentInstant())));
        cpuCol.setPrefWidth(60);

        var memCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.memory"));
        memCol.setCellValueFactory(d -> new SimpleStringProperty(
            String.format(Locale.US, "%.1f", d.getValue().memory().percentOfTotal())));
        memCol.setPrefWidth(60);

        table.getColumns().addAll(pidCol, nameCol, cpuCol, memCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(toolbar, table);

        refresh();
    }

    @Override
    public void refresh() {
        viewModel.refresh();
    }
}
