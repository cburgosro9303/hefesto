package org.iumotionlabs.hefesto.desktop.dashboard;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.api.widget.WidgetDescriptor;
import org.iumotionlabs.hefesto.desktop.framework.FeatureRegistry;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;

import java.util.List;

public class DashboardViewModel extends BaseViewModel {

    private final ObservableList<WidgetDescriptor> activeWidgets = FXCollections.observableArrayList();
    private final FeatureRegistry featureRegistry;
    private final DashboardConfig config = new DashboardConfig();

    public DashboardViewModel(FeatureRegistry featureRegistry) {
        this.featureRegistry = featureRegistry;
        activeWidgets.setAll(featureRegistry.getAllWidgets());
    }

    public ObservableList<WidgetDescriptor> getActiveWidgets() { return activeWidgets; }

    public List<WidgetDescriptor> getAvailableWidgets() {
        return featureRegistry.getAllWidgets();
    }

    public void addWidget(WidgetDescriptor descriptor) {
        if (!activeWidgets.contains(descriptor)) {
            activeWidgets.add(descriptor);
        }
    }

    public void removeWidget(WidgetDescriptor descriptor) {
        activeWidgets.remove(descriptor);
        config.removeLayout(descriptor.id());
    }

    public DashboardConfig getConfig() { return config; }
}
