package org.iumotionlabs.hefesto.desktop.observability;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;

public class HealthStatusViewModel extends BaseViewModel {

    public enum HealthState { HEALTHY, UNHEALTHY, UNKNOWN }

    public record ModuleHealth(String moduleName, HealthState state, String details) {}

    private final ObservableList<ModuleHealth> modules = FXCollections.observableArrayList();

    public HealthStatusViewModel() {
        modules.addAll(
            new ModuleHealth("PortInfo Service", HealthState.HEALTHY, "Operational"),
            new ModuleHealth("ProcessMonitor Service", HealthState.HEALTHY, "Operational"),
            new ModuleHealth("HealthCheck Service", HealthState.HEALTHY, "Operational"),
            new ModuleHealth("Docker Service", HealthState.UNKNOWN, "Not yet checked"),
            new ModuleHealth("Security Analysis", HealthState.HEALTHY, "Operational")
        );
    }

    public ObservableList<ModuleHealth> getModules() { return modules; }

    public void refresh() {
        // Periodic health polling would go here
    }
}
