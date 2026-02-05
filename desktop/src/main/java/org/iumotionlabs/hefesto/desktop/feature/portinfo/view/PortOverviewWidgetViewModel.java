package org.iumotionlabs.hefesto.desktop.feature.portinfo.view;

import javafx.beans.property.*;
import org.iumotionlabs.hefesto.desktop.ServiceLocator;
import org.iumotionlabs.hefesto.desktop.concurrency.HefestoExecutors;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;
import org.iumotionlabs.hefesto.feature.portinfo.service.PortInfoService;

public class PortOverviewWidgetViewModel extends BaseViewModel {

    private final IntegerProperty totalListening = new SimpleIntegerProperty(0);
    private final IntegerProperty exposedCount = new SimpleIntegerProperty(0);
    private final IntegerProperty securityIssueCount = new SimpleIntegerProperty(0);

    public IntegerProperty totalListeningProperty() { return totalListening; }
    public IntegerProperty exposedCountProperty() { return exposedCount; }
    public IntegerProperty securityIssueCountProperty() { return securityIssueCount; }

    public void refresh() {
        setBusy(true);
        HefestoExecutors.runAsync(
            () -> ServiceLocator.get(PortInfoService.class).getNetworkOverview(),
            overview -> {
                totalListening.set(overview.statistics().totalListening());
                exposedCount.set(overview.statistics().exposedCount());
                // Security issue count from report
                var report = ServiceLocator.get(
                    org.iumotionlabs.hefesto.feature.portinfo.service.SecurityAnalysisService.class
                ).analyzeEnriched(overview.bindings());
                securityIssueCount.set((int) (report.criticalCount() + report.highCount()));
                setBusy(false);
            },
            error -> {
                setError(error.getMessage());
                setBusy(false);
            }
        );
    }
}
