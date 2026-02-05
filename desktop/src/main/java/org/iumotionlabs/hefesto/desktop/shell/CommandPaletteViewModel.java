package org.iumotionlabs.hefesto.desktop.shell;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.api.action.ActionDescriptor;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.framework.FeatureRegistry;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;

import java.util.List;

public class CommandPaletteViewModel extends BaseViewModel {

    public record PaletteEntry(String label, String category, Runnable action) {}

    private final StringProperty searchQuery = new SimpleStringProperty("");
    private final ObservableList<PaletteEntry> filteredEntries = FXCollections.observableArrayList();
    private final List<PaletteEntry> allEntries;

    public CommandPaletteViewModel(FeatureRegistry registry, java.util.function.Consumer<NavigationContribution> navigator) {
        var entries = new java.util.ArrayList<PaletteEntry>();
        var i18n = I18nService.getInstance();

        for (var nav : registry.getAllNavigationItems()) {
            entries.add(new PaletteEntry(
                i18n.t(nav.labelKey()),
                "Navigation",
                () -> navigator.accept(nav)
            ));
        }

        for (var action : registry.getAllActions()) {
            entries.add(new PaletteEntry(
                i18n.t(action.labelKey()),
                "Action: " + action.category(),
                () -> {} // action execution handled separately
            ));
        }

        this.allEntries = List.copyOf(entries);
        filteredEntries.setAll(allEntries);

        searchQuery.addListener((_, _, query) -> filterEntries(query));
    }

    public StringProperty searchQueryProperty() { return searchQuery; }
    public ObservableList<PaletteEntry> getFilteredEntries() { return filteredEntries; }

    private void filterEntries(String query) {
        if (query == null || query.isBlank()) {
            filteredEntries.setAll(allEntries);
            return;
        }
        var lower = query.toLowerCase();
        filteredEntries.setAll(
            allEntries.stream()
                .filter(e -> e.label().toLowerCase().contains(lower) ||
                             e.category().toLowerCase().contains(lower))
                .toList()
        );
    }
}
