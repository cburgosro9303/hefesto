package org.iumotionlabs.hefesto.desktop.feature.portinfo.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.iumotionlabs.hefesto.desktop.controls.*;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;
import org.iumotionlabs.hefesto.feature.portinfo.model.EnrichedPortBinding;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class NetworkExplorerView extends SplitPane {

    private final NetworkExplorerViewModel viewModel = new NetworkExplorerViewModel();
    private final VirtualizedTableView<EnrichedPortBinding> table = new VirtualizedTableView<>();
    private final PortDetailView detailView = new PortDetailView();

    public NetworkExplorerView() {
        var i18n = I18nService.getInstance();
        getStyleClass().add("network-explorer");
        setOrientation(Orientation.HORIZONTAL);
        setDividerPositions(0.65);

        // Stats header KPIs
        var totalCard = new KpiCard(i18n.t("portinfo.total.listening"), "0");
        var tcpCard = new KpiCard(i18n.t("portinfo.tcp.count"), "0");
        var udpCard = new KpiCard(i18n.t("portinfo.udp.count"), "0");

        viewModel.totalListeningProperty().addListener((_, _, v) -> totalCard.setValue(String.valueOf(v.intValue())));
        viewModel.tcpCountProperty().addListener((_, _, v) -> tcpCard.setValue(String.valueOf(v.intValue())));
        viewModel.udpCountProperty().addListener((_, _, v) -> udpCard.setValue(String.valueOf(v.intValue())));

        var statsRow = new HBox(8, totalCard, tcpCard, udpCard);
        statsRow.setAlignment(Pos.CENTER_LEFT);
        statsRow.setPadding(new Insets(8));

        // Toolbar
        var searchField = new SearchField();
        searchField.debouncedTextProperty().addListener((_, _, text) -> {
            viewModel.filterTextProperty().set(text);
            refreshTable();
        });

        var protocolFilter = new ComboBox<String>();
        protocolFilter.getItems().addAll("ALL", "TCP", "UDP");
        protocolFilter.setValue("ALL");
        protocolFilter.valueProperty().addListener((_, _, val) -> {
            viewModel.protocolFilterProperty().set(val);
            refreshTable();
        });

        var refreshBtn = new RefreshButton();
        refreshBtn.setOnAction(_ -> {
            refreshBtn.playAnimation();
            viewModel.loadData();
        });

        var exportBtn = new Button(i18n.t("portinfo.export.csv"));
        exportBtn.setOnAction(_ -> exportCsv());

        HBox.setHgrow(searchField, Priority.ALWAYS);
        var toolbar = new HBox(8, searchField, protocolFilter, refreshBtn, exportBtn);
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        setupTable(i18n);

        // Context menu
        var contextMenu = new ContextMenu();
        var killItem = new MenuItem(i18n.t("portinfo.kill.process"));
        killItem.setOnAction(_ -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                ProcessKillDialog.show(selected.pid(), selected.processName());
            }
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
        contextMenu.getItems().addAll(killItem, new SeparatorMenuItem(), copyPidItem);
        table.setContextMenu(contextMenu);

        var leftPane = new VBox(statsRow, toolbar, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        getItems().addAll(leftPane, detailView);

        // Selection handler
        table.getSelectionModel().selectedItemProperty().addListener((_, _, selected) -> {
            viewModel.selectedBindingProperty().set(selected);
            detailView.showBinding(selected);
        });

        // Bind loading state
        var loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.visibleProperty().bind(viewModel.busyProperty());
        table.setPlaceholder(loadingIndicator);

        // Initial load
        viewModel.loadData();
        viewModel.getBindings().addListener((javafx.collections.ListChangeListener<EnrichedPortBinding>) _ -> refreshTable());
    }

    private void setupTable(I18nService i18n) {
        var portCol = new TableColumn<EnrichedPortBinding, String>(i18n.t("portinfo.port"));
        portCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().port())));
        portCol.setPrefWidth(70);

        var protoCol = new TableColumn<EnrichedPortBinding, String>(i18n.t("portinfo.protocol"));
        protoCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().protocol()));
        protoCol.setPrefWidth(70);

        var addrCol = new TableColumn<EnrichedPortBinding, String>(i18n.t("portinfo.address"));
        addrCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().localAddress()));
        addrCol.setPrefWidth(120);

        var stateCol = new TableColumn<EnrichedPortBinding, String>(i18n.t("portinfo.state"));
        stateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().state()));
        stateCol.setPrefWidth(80);

        var processCol = new TableColumn<EnrichedPortBinding, String>(i18n.t("portinfo.process"));
        processCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().processName()));
        processCol.setPrefWidth(120);

        var pidCol = new TableColumn<EnrichedPortBinding, String>(i18n.t("portinfo.pid"));
        pidCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().pid())));
        pidCol.setPrefWidth(70);

        var secCol = new TableColumn<EnrichedPortBinding, String>(i18n.t("portinfo.security"));
        secCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isExposed() ? "EXPOSED" : "LOCAL"));
        secCol.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    var badge = new StatusBadge(item,
                        "EXPOSED".equals(item) ? StatusBadge.Severity.WARNING : StatusBadge.Severity.SUCCESS);
                    setGraphic(badge);
                }
            }
        });
        secCol.setPrefWidth(90);

        table.getColumns().addAll(portCol, protoCol, addrCol, stateCol, processCol, pidCol, secCol);
        table.setSourceItems(viewModel.getBindings());
    }

    private void refreshTable() {
        table.setFilter(b -> {
            var filter = viewModel.filterTextProperty().get();
            var protocol = viewModel.protocolFilterProperty().get();

            boolean matchesProtocol = "ALL".equals(protocol) || b.protocol().equalsIgnoreCase(protocol);
            boolean matchesFilter = filter == null || filter.isEmpty() ||
                String.valueOf(b.port()).contains(filter) ||
                b.processName().toLowerCase().contains(filter.toLowerCase()) ||
                b.localAddress().contains(filter);

            return matchesProtocol && matchesFilter;
        });
    }

    private void exportCsv() {
        var csv = table.exportToCsv();
        var chooser = new FileChooser();
        chooser.setTitle("Export CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("network-ports.csv");
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), csv);
            } catch (IOException e) {
                // handled silently
            }
        }
    }
}
