package org.iumotionlabs.hefesto.desktop.feature.portinfo.view;

import javafx.beans.property.*;
import org.iumotionlabs.hefesto.desktop.ServiceLocator;
import org.iumotionlabs.hefesto.desktop.concurrency.HefestoExecutors;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;
import org.iumotionlabs.hefesto.feature.portinfo.model.DockerInfo;
import org.iumotionlabs.hefesto.feature.portinfo.model.EnrichedPortBinding;
import org.iumotionlabs.hefesto.feature.portinfo.model.HealthCheckResult;
import org.iumotionlabs.hefesto.feature.portinfo.model.SecurityFlag;
import org.iumotionlabs.hefesto.feature.portinfo.service.DockerService;
import org.iumotionlabs.hefesto.feature.portinfo.service.HealthCheckService;
import org.iumotionlabs.hefesto.feature.portinfo.service.SecurityAnalysisService;

import java.util.List;

public class PortDetailViewModel extends BaseViewModel {

    private final ObjectProperty<EnrichedPortBinding> binding = new SimpleObjectProperty<>();
    private final ObjectProperty<HealthCheckResult> healthResult = new SimpleObjectProperty<>();
    private final ObjectProperty<List<SecurityFlag>> securityFlags = new SimpleObjectProperty<>();
    private final ObjectProperty<DockerInfo> dockerInfo = new SimpleObjectProperty<>();
    private final BooleanProperty healthCheckRunning = new SimpleBooleanProperty(false);

    public ObjectProperty<EnrichedPortBinding> bindingProperty() { return binding; }
    public ObjectProperty<HealthCheckResult> healthResultProperty() { return healthResult; }
    public ObjectProperty<List<SecurityFlag>> securityFlagsProperty() { return securityFlags; }
    public ObjectProperty<DockerInfo> dockerInfoProperty() { return dockerInfo; }
    public BooleanProperty healthCheckRunningProperty() { return healthCheckRunning; }

    public void setBinding(EnrichedPortBinding enriched) {
        binding.set(enriched);
        healthResult.set(null);
        securityFlags.set(null);
        dockerInfo.set(null);

        if (enriched != null) {
            loadSecurityAnalysis(enriched);
            loadDockerInfo(enriched);
        }
    }

    public void runHealthCheck() {
        var b = binding.get();
        if (b == null) return;

        healthCheckRunning.set(true);
        HefestoExecutors.runAsync(
            () -> ServiceLocator.get(HealthCheckService.class).checkComprehensive(
                b.localAddress().equals("0.0.0.0") ? "127.0.0.1" : b.localAddress(),
                b.port(), true, true),
            result -> {
                healthResult.set(result);
                healthCheckRunning.set(false);
            },
            error -> {
                setError(error.getMessage());
                healthCheckRunning.set(false);
            }
        );
    }

    private void loadSecurityAnalysis(EnrichedPortBinding enriched) {
        HefestoExecutors.runAsync(
            () -> ServiceLocator.get(SecurityAnalysisService.class).analyze(enriched.binding()),
            flags -> securityFlags.set(flags),
            error -> setError(error.getMessage())
        );
    }

    private void loadDockerInfo(EnrichedPortBinding enriched) {
        var dockerService = ServiceLocator.get(DockerService.class);
        if (!dockerService.isDockerAvailable()) return;

        HefestoExecutors.runAsync(
            () -> dockerService.getContainerByPid(enriched.pid()).orElse(null),
            info -> { if (info != null) dockerInfo.set(info); },
            error -> { /* silently ignore Docker lookup failures */ }
        );
    }
}
