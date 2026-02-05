package org.iumotionlabs.hefesto.desktop.feature.procwatch.view;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.ServiceLocator;
import org.iumotionlabs.hefesto.desktop.concurrency.HefestoExecutors;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;
import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;
import org.iumotionlabs.hefesto.feature.procwatch.service.ProcessMonitorService;

public class TopProcessesWidgetViewModel extends BaseViewModel {

    private final ObservableList<ProcessSample> topProcesses = FXCollections.observableArrayList();
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private volatile boolean stopRequested = false;

    public ObservableList<ProcessSample> getTopProcesses() { return topProcesses; }
    public BooleanProperty playingProperty() { return playing; }

    public void refresh() {
        HefestoExecutors.runAsync(
            () -> ServiceLocator.get(ProcessMonitorService.class).topByCpu(10),
            processes -> topProcesses.setAll(processes),
            error -> setError(error.getMessage())
        );
    }

    public void startLiveUpdates() {
        stopRequested = false;
        playing.set(true);
        HefestoExecutors.runOnIoThread(() -> {
            while (!stopRequested) {
                try {
                    var processes = ServiceLocator.get(ProcessMonitorService.class).topByCpu(10);
                    Platform.runLater(() -> topProcesses.setAll(processes));
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Platform.runLater(() -> setError(e.getMessage()));
                    break;
                }
            }
            Platform.runLater(() -> playing.set(false));
        });
    }

    public void stopLiveUpdates() {
        stopRequested = true;
    }

    @Override
    public void dispose() {
        stopLiveUpdates();
        super.dispose();
    }
}
