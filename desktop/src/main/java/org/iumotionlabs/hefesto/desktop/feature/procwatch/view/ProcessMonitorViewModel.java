package org.iumotionlabs.hefesto.desktop.feature.procwatch.view;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.ServiceLocator;
import org.iumotionlabs.hefesto.desktop.concurrency.HefestoExecutors;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;
import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;
import org.iumotionlabs.hefesto.feature.procwatch.model.JvmMetrics;
import org.iumotionlabs.hefesto.feature.procwatch.service.ProcessMonitorService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class ProcessMonitorViewModel extends BaseViewModel {

    private static final AtomicLong INITIAL_PID = new AtomicLong(-1);

    private final StringProperty pidInput = new SimpleStringProperty("");
    private final StringProperty nameInput = new SimpleStringProperty("");
    private final BooleanProperty monitoring = new SimpleBooleanProperty(false);
    private final ObjectProperty<ProcessSample> currentSample = new SimpleObjectProperty<>();
    private final ObjectProperty<JvmMetrics> jvmMetrics = new SimpleObjectProperty<>();
    private final ObservableList<Double> cpuHistory = FXCollections.observableArrayList();
    private final ObservableList<Double> memoryHistory = FXCollections.observableArrayList();
    private final ObservableList<String> alerts = FXCollections.observableArrayList();
    private final ProcessMonitorService monitorService;

    private volatile boolean stopRequested = false;

    public ProcessMonitorViewModel() {
        this.monitorService = ServiceLocator.get(ProcessMonitorService.class);
    }

    public static void setInitialPid(long pid) {
        INITIAL_PID.set(pid);
    }

    public static long consumeInitialPid() {
        return INITIAL_PID.getAndSet(-1);
    }

    public StringProperty pidInputProperty() { return pidInput; }
    public StringProperty nameInputProperty() { return nameInput; }
    public BooleanProperty monitoringProperty() { return monitoring; }
    public ObjectProperty<ProcessSample> currentSampleProperty() { return currentSample; }
    public ObjectProperty<JvmMetrics> jvmMetricsProperty() { return jvmMetrics; }
    public ObservableList<Double> getCpuHistory() { return cpuHistory; }
    public ObservableList<Double> getMemoryHistory() { return memoryHistory; }
    public ObservableList<String> getAlerts() { return alerts; }

    public void startMonitoring() {
        var pidText = pidInput.get();
        if (pidText == null || pidText.isBlank()) return;

        long pid;
        try {
            pid = Long.parseLong(pidText.trim());
        } catch (NumberFormatException e) {
            setError("Invalid PID: " + pidText);
            return;
        }

        startMonitoringPid(pid);
    }

    public void startMonitoringByName() {
        var name = nameInput.get();
        if (name == null || name.isBlank()) return;

        stopRequested = false;
        monitoring.set(true);
        clearError();
        cpuHistory.clear();
        memoryHistory.clear();

        HefestoExecutors.runOnIoThread(() -> {
            try {
                List<ProcessSample> matches = monitorService.sampleByName(name);
                if (matches.isEmpty()) {
                    Platform.runLater(() -> {
                        setError("No process found matching: " + name);
                        monitoring.set(false);
                    });
                    return;
                }
                // Monitor the first match
                long pid = matches.getFirst().pid();
                Platform.runLater(() -> pidInput.set(String.valueOf(pid)));
                monitorLoop(pid);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setError(e.getMessage());
                    monitoring.set(false);
                });
            }
        });
    }

    public void startMonitoringPid(long pid) {
        stopRequested = false;
        monitoring.set(true);
        clearError();
        cpuHistory.clear();
        memoryHistory.clear();

        HefestoExecutors.runOnIoThread(() -> monitorLoop(pid));
    }

    private void monitorLoop(long pid) {
        while (!stopRequested) {
            try {
                Optional<ProcessSample> sample = monitorService.sampleByPid(pid);
                sample.ifPresent(s -> Platform.runLater(() -> {
                    currentSample.set(s);
                    cpuHistory.add(s.cpu().percentInstant());
                    memoryHistory.add(s.memory().percentOfTotal());

                    if (cpuHistory.size() > 60) cpuHistory.removeFirst();
                    if (memoryHistory.size() > 60) memoryHistory.removeFirst();
                }));

                if (sample.isEmpty()) {
                    Platform.runLater(() -> {
                        setError("Process " + pid + " not found");
                        stopMonitoring();
                    });
                    break;
                }

                // JVM metrics
                if (sample.get().isJavaProcess()) {
                    monitorService.getJvmMetrics(pid).ifPresent(jvm ->
                        Platform.runLater(() -> jvmMetrics.set(jvm))
                    );
                }

                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Platform.runLater(() -> setError(e.getMessage()));
                break;
            }
        }
        Platform.runLater(() -> monitoring.set(false));
    }

    public void stopMonitoring() {
        stopRequested = true;
    }

    @Override
    public void dispose() {
        stopMonitoring();
        super.dispose();
    }
}
