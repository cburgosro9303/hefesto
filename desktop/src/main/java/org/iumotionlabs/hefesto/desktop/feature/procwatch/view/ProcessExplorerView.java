package org.iumotionlabs.hefesto.desktop.feature.procwatch.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.iumotionlabs.hefesto.desktop.chart.TimeSeriesChart;
import org.iumotionlabs.hefesto.desktop.controls.*;
import org.iumotionlabs.hefesto.desktop.event.EventBus;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;
import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

public class ProcessExplorerView extends VBox {

    private final ProcessExplorerViewModel viewModel = new ProcessExplorerViewModel();
    private final VirtualizedTableView<ProcessSample> table = new VirtualizedTableView<>();

    public ProcessExplorerView() {
        var i18n = I18nService.getInstance();
        getStyleClass().add("process-explorer");
        setSpacing(10);
        setPadding(new Insets(10));

        // Header: System overview KPIs
        var totalCard = new KpiCard(i18n.t("procwatch.total.processes"), "0");
        var cpuCard = new KpiCard(i18n.t("procwatch.system.cpu"), "0%");
        var memCard = new KpiCard(i18n.t("procwatch.system.memory"), "0%");

        viewModel.totalProcessCountProperty().addListener((_, _, v) -> totalCard.setValue(String.valueOf(v.intValue())));
        viewModel.systemCpuPercentProperty().addListener((_, _, v) ->
            cpuCard.setValue(String.format(Locale.US, "%.1f%%", v.doubleValue())));
        viewModel.systemMemPercentProperty().addListener((_, _, v) ->
            memCard.setValue(String.format(Locale.US, "%.1f%%", v.doubleValue())));

        var kpiRow = new HBox(8, totalCard, cpuCard, memCard);
        kpiRow.setAlignment(Pos.CENTER_LEFT);

        // Mini charts for system CPU/Memory history
        var cpuChart = new TimeSeriesChart(i18n.t("procwatch.system.cpu"), "%", 60);
        var cpuSeries = cpuChart.addSeries("CPU");
        cpuChart.setPrefHeight(150);

        var memChart = new TimeSeriesChart(i18n.t("procwatch.system.memory"), "%", 60);
        var memSeries = memChart.addSeries("Memory");
        memChart.setPrefHeight(150);

        viewModel.systemCpuPercentProperty().addListener((_, _, v) ->
            cpuChart.addDataPoint(cpuSeries, v.doubleValue()));
        viewModel.systemMemPercentProperty().addListener((_, _, v) ->
            memChart.addDataPoint(memSeries, v.doubleValue()));

        var chartRow = new HBox(8, cpuChart, memChart);
        HBox.setHgrow(cpuChart, Priority.ALWAYS);
        HBox.setHgrow(memChart, Priority.ALWAYS);

        // Toolbar
        var searchField = new SearchField();
        searchField.debouncedTextProperty().addListener((_, _, text) -> {
            viewModel.searchTextProperty().set(text);
            table.setFilter(viewModel.createFilter());
        });

        var sortCombo = new ComboBox<String>();
        sortCombo.getItems().addAll(
            i18n.t("procwatch.sort.cpu"),
            i18n.t("procwatch.sort.memory"),
            "PID",
            i18n.t("procwatch.process")
        );
        sortCombo.setValue(i18n.t("procwatch.sort.cpu"));
        sortCombo.valueProperty().addListener((_, _, val) -> {
            if (val.equals(i18n.t("procwatch.sort.cpu"))) viewModel.sortModeProperty().set(ProcessExplorerViewModel.SortMode.CPU);
            else if (val.equals(i18n.t("procwatch.sort.memory"))) viewModel.sortModeProperty().set(ProcessExplorerViewModel.SortMode.MEMORY);
            else if (val.equals("PID")) viewModel.sortModeProperty().set(ProcessExplorerViewModel.SortMode.PID);
            else viewModel.sortModeProperty().set(ProcessExplorerViewModel.SortMode.NAME);
            reloadSorted();
        });

        var autoRefreshBtn = new ToggleButton(i18n.t("procwatch.auto.refresh"));
        autoRefreshBtn.selectedProperty().addListener((_, _, selected) -> {
            if (selected) viewModel.startAutoRefresh();
            else viewModel.stopAutoRefresh();
        });
        viewModel.autoRefreshProperty().addListener((_, _, v) -> autoRefreshBtn.setSelected(v));

        var refreshBtn = new RefreshButton();
        refreshBtn.setOnAction(_ -> {
            refreshBtn.playAnimation();
            viewModel.loadProcesses();
        });

        var exportBtn = new Button(i18n.t("portinfo.export.csv"));
        exportBtn.setOnAction(_ -> exportCsv());

        HBox.setHgrow(searchField, Priority.ALWAYS);
        var toolbar = new HBox(8, searchField, sortCombo, autoRefreshBtn, refreshBtn, exportBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Table
        setupTable(i18n);

        // Context menu
        var contextMenu = new ContextMenu();
        var monitorItem = new MenuItem(i18n.t("procwatch.monitor.process"));
        monitorItem.setOnAction(_ -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) openMonitor(selected.pid());
        });
        var killItem = new MenuItem(i18n.t("procwatch.kill.process"));
        killItem.setOnAction(_ -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) ProcessKillDialog.show(selected.pid(), selected.name());
        });
        var copyPidItem = new MenuItem(i18n.t("procwatch.copy.pid"));
        copyPidItem.setOnAction(_ -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                var content = new ClipboardContent();
                content.putString(String.valueOf(selected.pid()));
                Clipboard.getSystemClipboard().setContent(content);
            }
        });
        contextMenu.getItems().addAll(monitorItem, killItem, new SeparatorMenuItem(), copyPidItem);
        table.setContextMenu(contextMenu);

        // Double-click to monitor
        table.setRowFactory(_ -> {
            var row = new TableRow<ProcessSample>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY && !row.isEmpty()) {
                    openMonitor(row.getItem().pid());
                }
            });
            return row;
        });

        // Loading indicator
        var loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.visibleProperty().bind(viewModel.busyProperty());
        table.setPlaceholder(loadingIndicator);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(kpiRow, chartRow, toolbar, table);

        // Initial load
        viewModel.loadProcesses();
    }

    private void setupTable(I18nService i18n) {
        var pidCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.pid"));
        pidCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().pid())));
        pidCol.setPrefWidth(70);

        var nameCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.process"));
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        nameCol.setPrefWidth(140);

        var stateCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.state"));
        stateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().state().description()));
        stateCol.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    var severity = switch (item) {
                        case "Running" -> StatusBadge.Severity.SUCCESS;
                        case "Zombie" -> StatusBadge.Severity.CRITICAL;
                        case "Stopped" -> StatusBadge.Severity.WARNING;
                        case "Sleeping", "Idle" -> StatusBadge.Severity.INFO;
                        default -> StatusBadge.Severity.INFO;
                    };
                    setGraphic(new StatusBadge(item, severity));
                }
            }
        });
        stateCol.setPrefWidth(80);

        var cpuCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.cpu"));
        cpuCol.setCellValueFactory(data -> new SimpleStringProperty(
            String.format(Locale.US, "%.1f", data.getValue().cpu().percentInstant())));
        cpuCol.setPrefWidth(60);

        var memCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.memory"));
        memCol.setCellValueFactory(data -> new SimpleStringProperty(
            String.format(Locale.US, "%.1f", data.getValue().memory().percentOfTotal())));
        memCol.setPrefWidth(60);

        var rssCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.rss"));
        rssCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().memory().rssFormatted()));
        rssCol.setPrefWidth(80);

        var threadsCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.threads"));
        threadsCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().threadCount())));
        threadsCol.setPrefWidth(60);

        var userCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.user"));
        userCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().user()));
        userCol.setPrefWidth(80);

        var javaCol = new TableColumn<ProcessSample, String>(i18n.t("procwatch.java.process"));
        javaCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isJavaProcess() ? "Java" : ""));
        javaCol.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    var badge = new Label("Java");
                    badge.getStyleClass().add("java-badge");
                    setGraphic(badge);
                }
            }
        });
        javaCol.setPrefWidth(50);

        table.getColumns().addAll(pidCol, nameCol, stateCol, cpuCol, memCol, rssCol, threadsCol, userCol, javaCol);
        table.setSourceItems(viewModel.getAllProcesses());
    }

    private void openMonitor(long pid) {
        ProcessMonitorViewModel.setInitialPid(pid);
        EventBus.getInstance().publish(
            new org.iumotionlabs.hefesto.desktop.api.event.NavigationRequested("procwatch.monitor")
        );
    }

    private void exportCsv() {
        var csv = table.exportToCsv();
        var chooser = new FileChooser();
        chooser.setTitle("Export CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("processes.csv");
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), csv);
            } catch (IOException e) {
                // handled silently
            }
        }
    }

    private void reloadSorted() {
        var sorted = viewModel.getAllProcesses().sorted(viewModel.createComparator());
        viewModel.getAllProcesses().setAll(sorted);
    }
}
