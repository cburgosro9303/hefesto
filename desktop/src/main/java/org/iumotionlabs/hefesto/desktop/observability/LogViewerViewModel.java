package org.iumotionlabs.hefesto.desktop.observability;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogViewerViewModel extends BaseViewModel {

    private static final int MAX_LOG_ENTRIES = 500;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public record LogEntry(String timestamp, String level, String message) {}

    private final ObservableList<LogEntry> allEntries = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> filteredEntries = FXCollections.observableArrayList();
    private final StringProperty levelFilter = new SimpleStringProperty("ALL");
    private final StringProperty searchFilter = new SimpleStringProperty("");

    public LogViewerViewModel() {
        levelFilter.addListener((_, _, _) -> applyFilters());
        searchFilter.addListener((_, _, _) -> applyFilters());
    }

    public ObservableList<LogEntry> getFilteredEntries() { return filteredEntries; }
    public StringProperty levelFilterProperty() { return levelFilter; }
    public StringProperty searchFilterProperty() { return searchFilter; }

    public void addLog(String level, String message) {
        var entry = new LogEntry(LocalDateTime.now().format(FMT), level, message);
        scheduler.runOnUiThread(() -> {
            allEntries.add(entry);
            if (allEntries.size() > MAX_LOG_ENTRIES) {
                allEntries.removeFirst();
            }
            applyFilters();
        });
    }

    private void applyFilters() {
        var level = levelFilter.get();
        var search = searchFilter.get();
        filteredEntries.setAll(
            allEntries.stream()
                .filter(e -> "ALL".equals(level) || e.level().equalsIgnoreCase(level))
                .filter(e -> search == null || search.isEmpty() || e.message().toLowerCase().contains(search.toLowerCase()))
                .toList()
        );
    }

    public void clear() {
        allEntries.clear();
        filteredEntries.clear();
    }
}
