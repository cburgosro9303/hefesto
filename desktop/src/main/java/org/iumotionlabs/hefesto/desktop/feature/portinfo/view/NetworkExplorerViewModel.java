package org.iumotionlabs.hefesto.desktop.feature.portinfo.view;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.ServiceLocator;
import org.iumotionlabs.hefesto.desktop.concurrency.HefestoExecutors;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;
import org.iumotionlabs.hefesto.feature.portinfo.model.EnrichedPortBinding;
import org.iumotionlabs.hefesto.feature.portinfo.model.NetworkOverview;
import org.iumotionlabs.hefesto.feature.portinfo.service.PortInfoService;

import java.util.List;

public class NetworkExplorerViewModel extends BaseViewModel {

    private final ObservableList<EnrichedPortBinding> bindings = FXCollections.observableArrayList();
    private final ObjectProperty<EnrichedPortBinding> selectedBinding = new SimpleObjectProperty<>();
    private final StringProperty filterText = new SimpleStringProperty("");
    private final StringProperty protocolFilter = new SimpleStringProperty("ALL");
    private final IntegerProperty totalListening = new SimpleIntegerProperty(0);
    private final IntegerProperty tcpCount = new SimpleIntegerProperty(0);
    private final IntegerProperty udpCount = new SimpleIntegerProperty(0);
    private final PortInfoService portInfoService;

    public NetworkExplorerViewModel() {
        this.portInfoService = ServiceLocator.get(PortInfoService.class);
    }

    public ObservableList<EnrichedPortBinding> getBindings() { return bindings; }
    public ObjectProperty<EnrichedPortBinding> selectedBindingProperty() { return selectedBinding; }
    public StringProperty filterTextProperty() { return filterText; }
    public StringProperty protocolFilterProperty() { return protocolFilter; }
    public IntegerProperty totalListeningProperty() { return totalListening; }
    public IntegerProperty tcpCountProperty() { return tcpCount; }
    public IntegerProperty udpCountProperty() { return udpCount; }

    public void loadData() {
        setBusy(true);
        clearError();
        HefestoExecutors.runAsync(
            () -> {
                var allListening = portInfoService.findAllListening();
                var enriched = portInfoService.enrichBindings(allListening);
                var overview = NetworkOverview.from(enriched);
                return new DataResult(enriched, overview);
            },
            result -> {
                bindings.setAll(result.bindings);
                var stats = result.overview.statistics();
                totalListening.set(stats.totalListening());
                tcpCount.set(stats.tcpCount());
                udpCount.set(stats.udpCount());
                setBusy(false);
            },
            error -> {
                setError(error.getMessage());
                setBusy(false);
            }
        );
    }

    public List<EnrichedPortBinding> getFilteredBindings() {
        var filter = filterText.get();
        var protocol = protocolFilter.get();

        return bindings.stream()
            .filter(b -> "ALL".equals(protocol) || b.protocol().equalsIgnoreCase(protocol))
            .filter(b -> filter == null || filter.isEmpty() ||
                         String.valueOf(b.port()).contains(filter) ||
                         b.processName().toLowerCase().contains(filter.toLowerCase()) ||
                         b.localAddress().contains(filter))
            .toList();
    }

    private record DataResult(List<EnrichedPortBinding> bindings, NetworkOverview overview) {}
}
