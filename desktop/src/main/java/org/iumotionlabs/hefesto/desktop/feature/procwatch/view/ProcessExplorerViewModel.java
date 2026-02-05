package org.iumotionlabs.hefesto.desktop.feature.procwatch.view;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.ServiceLocator;
import org.iumotionlabs.hefesto.desktop.concurrency.HefestoExecutors;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;
import org.iumotionlabs.hefesto.feature.procwatch.model.ProcessSample;
import org.iumotionlabs.hefesto.feature.procwatch.service.ProcessMonitorService;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ProcessExplorerViewModel extends BaseViewModel {

    public enum SortMode { CPU, MEMORY, PID, NAME }

    private final ObservableList<ProcessSample> allProcesses = FXCollections.observableArrayList();
    private final StringProperty searchText = new SimpleStringProperty("");
    private final ObjectProperty<SortMode> sortMode = new SimpleObjectProperty<>(SortMode.CPU);
    private final BooleanProperty autoRefresh = new SimpleBooleanProperty(false);
    private final DoubleProperty systemCpuPercent = new SimpleDoubleProperty(0);
    private final DoubleProperty systemMemPercent = new SimpleDoubleProperty(0);
    private final IntegerProperty totalProcessCount = new SimpleIntegerProperty(0);
    private final ProcessMonitorService monitorService;

    private volatile boolean stopRequested = false;

    public ProcessExplorerViewModel() {
        this.monitorService = ServiceLocator.get(ProcessMonitorService.class);
    }

    public ObservableList<ProcessSample> getAllProcesses() { return allProcesses; }
    public StringProperty searchTextProperty() { return searchText; }
    public ObjectProperty<SortMode> sortModeProperty() { return sortMode; }
    public BooleanProperty autoRefreshProperty() { return autoRefresh; }
    public DoubleProperty systemCpuPercentProperty() { return systemCpuPercent; }
    public DoubleProperty systemMemPercentProperty() { return systemMemPercent; }
    public IntegerProperty totalProcessCountProperty() { return totalProcessCount; }

    public void loadProcesses() {
        setBusy(true);
        clearError();
        HefestoExecutors.runAsync(
            () -> monitorService.getAllProcesses(),
            processes -> {
                updateProcessList(processes);
                setBusy(false);
            },
            error -> {
                setError(error.getMessage());
                setBusy(false);
            }
        );
    }

    public void startAutoRefresh() {
        stopRequested = false;
        autoRefresh.set(true);
        HefestoExecutors.runOnIoThread(() -> {
            while (!stopRequested) {
                try {
                    List<ProcessSample> processes = monitorService.getAllProcesses();
                    Platform.runLater(() -> updateProcessList(processes));
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Platform.runLater(() -> setError(e.getMessage()));
                    break;
                }
            }
            Platform.runLater(() -> autoRefresh.set(false));
        });
    }

    public void stopAutoRefresh() {
        stopRequested = true;
    }

    private void updateProcessList(List<ProcessSample> processes) {
        allProcesses.setAll(processes);
        totalProcessCount.set(processes.size());

        var systemInfo = monitorService.getSystemInfo();
        double totalCpu = processes.stream().mapToDouble(p -> p.cpu().percentInstant()).sum();
        double normalizedCpu = systemInfo.cpuCount() > 0 ? totalCpu / systemInfo.cpuCount() : totalCpu;
        systemCpuPercent.set(Math.min(normalizedCpu, 100.0));

        long totalRss = processes.stream().mapToLong(p -> p.memory().rssBytes()).sum();
        double memPercent = systemInfo.totalMemoryBytes() > 0
            ? (totalRss * 100.0) / systemInfo.totalMemoryBytes() : 0;
        systemMemPercent.set(Math.min(memPercent, 100.0));
    }

    public java.util.function.Predicate<ProcessSample> createFilter() {
        String text = searchText.get();
        if (text == null || text.isBlank()) return _ -> true;
        String lower = text.toLowerCase(Locale.ROOT);
        return p -> String.valueOf(p.pid()).contains(lower)
            || p.name().toLowerCase(Locale.ROOT).contains(lower)
            || p.commandLine().toLowerCase(Locale.ROOT).contains(lower);
    }

    public Comparator<ProcessSample> createComparator() {
        return switch (sortMode.get()) {
            case CPU -> Comparator.comparingDouble((ProcessSample p) -> p.cpu().percentInstant()).reversed();
            case MEMORY -> Comparator.comparingDouble((ProcessSample p) -> p.memory().percentOfTotal()).reversed();
            case PID -> Comparator.comparingLong(ProcessSample::pid);
            case NAME -> Comparator.comparing(ProcessSample::name, String.CASE_INSENSITIVE_ORDER);
        };
    }

    @Override
    public void dispose() {
        stopAutoRefresh();
        super.dispose();
    }
}
