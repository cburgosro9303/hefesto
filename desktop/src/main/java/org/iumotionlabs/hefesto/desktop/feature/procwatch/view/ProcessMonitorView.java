package org.iumotionlabs.hefesto.desktop.feature.procwatch.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.chart.GaugeChart;
import org.iumotionlabs.hefesto.desktop.chart.TimeSeriesChart;
import org.iumotionlabs.hefesto.desktop.controls.KpiCard;
import org.iumotionlabs.hefesto.desktop.controls.ProcessKillDialog;
import org.iumotionlabs.hefesto.desktop.controls.StatusBadge;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

public class ProcessMonitorView extends VBox {

    private final ProcessMonitorViewModel viewModel = new ProcessMonitorViewModel();
    private final TimeSeriesChart cpuChart;
    private final TimeSeriesChart memChart;
    private final GaugeChart cpuGauge;
    private final GaugeChart memGauge;
    private final StatusBadge stateBadge = new StatusBadge("--", StatusBadge.Severity.INFO);

    public ProcessMonitorView() {
        var i18n = I18nService.getInstance();
        setSpacing(10);
        setPadding(new Insets(10));

        // Toolbar - PID input
        var pidField = new TextField();
        pidField.setPromptText("Enter PID...");
        pidField.setPrefWidth(120);
        pidField.textProperty().bindBidirectional(viewModel.pidInputProperty());

        // Name search input
        var nameField = new TextField();
        nameField.setPromptText(i18n.t("procwatch.search.name"));
        nameField.setPrefWidth(160);
        nameField.textProperty().bindBidirectional(viewModel.nameInputProperty());

        var searchByNameBtn = new Button(i18n.t("common.search").replace("...", ""));
        searchByNameBtn.setOnAction(_ -> viewModel.startMonitoringByName());
        searchByNameBtn.disableProperty().bind(viewModel.monitoringProperty());

        var startBtn = new Button(i18n.t("execution.start"));
        startBtn.setOnAction(_ -> viewModel.startMonitoring());
        startBtn.disableProperty().bind(viewModel.monitoringProperty());

        var stopBtn = new Button(i18n.t("execution.stop"));
        stopBtn.setOnAction(_ -> viewModel.stopMonitoring());
        stopBtn.disableProperty().bind(viewModel.monitoringProperty().not());

        var killBtn = new Button(i18n.t("procwatch.kill.process"));
        killBtn.getStyleClass().add("kill-button");
        killBtn.setOnAction(_ -> {
            var sample = viewModel.currentSampleProperty().get();
            if (sample != null) {
                ProcessKillDialog.show(sample.pid(), sample.name());
            }
        });
        killBtn.disableProperty().bind(viewModel.currentSampleProperty().isNull());

        var toolbar = new HBox(8,
            new Label("PID:"), pidField, startBtn,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            new Label(i18n.t("procwatch.process") + ":"), nameField, searchByNameBtn,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            stopBtn, killBtn
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 8, 0));

        // Gauges
        cpuGauge = new GaugeChart(120);
        cpuGauge.labelProperty().set("CPU");
        memGauge = new GaugeChart(120);
        memGauge.labelProperty().set("Memory");

        var gaugeRow = new HBox(20, cpuGauge, memGauge);
        gaugeRow.setAlignment(Pos.CENTER);

        // KPI cards
        var pidCard = new KpiCard("PID", "--");
        var processCard = new KpiCard("Process", "--");
        var threadsCard = new KpiCard("Threads", "--");
        var uptimeCard = new KpiCard("Uptime", "--");
        var ioReadCard = new KpiCard(i18n.t("procwatch.io.read"), "--");
        var ioWriteCard = new KpiCard(i18n.t("procwatch.io.write"), "--");

        var kpiRow = new HBox(8, pidCard, processCard, threadsCard, uptimeCard, ioReadCard, ioWriteCard);
        kpiRow.setAlignment(Pos.CENTER);

        // State badge row
        var stateLabel = new Label(i18n.t("procwatch.state") + ":");
        stateLabel.setStyle("-fx-font-weight: bold;");
        var stateRow = new HBox(8, stateLabel, stateBadge);
        stateRow.setAlignment(Pos.CENTER_LEFT);

        // Charts
        cpuChart = new TimeSeriesChart("CPU Usage", "%", 60);
        var cpuSeries = cpuChart.addSeries("CPU %");
        memChart = new TimeSeriesChart("Memory Usage", "%", 60);
        var memSeries = memChart.addSeries("Memory %");

        cpuChart.setPrefHeight(200);
        memChart.setPrefHeight(200);

        // Alert panel
        var alertList = new ListView<String>();
        alertList.setItems(viewModel.getAlerts());
        alertList.setPrefHeight(100);
        var alertPane = new TitledPane("Alerts", alertList);
        alertPane.setExpanded(false);

        // Error display
        var errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        // Bind sample updates
        viewModel.currentSampleProperty().addListener((_, _, sample) -> {
            if (sample == null) return;
            cpuGauge.setValue(sample.cpu().percentInstant());
            memGauge.setValue(sample.memory().percentOfTotal());
            cpuChart.addDataPoint(cpuSeries, sample.cpu().percentInstant());
            memChart.addDataPoint(memSeries, sample.memory().percentOfTotal());
            pidCard.setValue(String.valueOf(sample.pid()));
            processCard.setValue(sample.name());
            threadsCard.setValue(String.valueOf(sample.threadCount()));
            uptimeCard.setValue(sample.uptimeFormatted());

            // IO metrics
            if (sample.io() != null) {
                ioReadCard.setValue(sample.io().readFormatted());
                ioWriteCard.setValue(sample.io().writeFormatted());
            }

            // State badge
            var stateDesc = sample.state().description();
            stateBadge.setText(stateDesc);
            stateBadge.setSeverity(switch (sample.state()) {
                case RUNNING -> StatusBadge.Severity.SUCCESS;
                case ZOMBIE -> StatusBadge.Severity.CRITICAL;
                case STOPPED -> StatusBadge.Severity.WARNING;
                case SLEEPING, IDLE, WAITING -> StatusBadge.Severity.INFO;
                case UNKNOWN -> StatusBadge.Severity.INFO;
            });
        });

        var scrollContent = new VBox(10, toolbar, errorLabel, stateRow, gaugeRow, kpiRow, cpuChart, memChart, alertPane);
        var scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().add(scrollPane);

        // Check for initial PID from Process Explorer drill-down
        long initialPid = ProcessMonitorViewModel.consumeInitialPid();
        if (initialPid > 0) {
            viewModel.pidInputProperty().set(String.valueOf(initialPid));
            viewModel.startMonitoringPid(initialPid);
        }
    }
}
