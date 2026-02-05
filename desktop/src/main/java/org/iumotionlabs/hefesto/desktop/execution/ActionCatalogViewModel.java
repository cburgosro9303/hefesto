package org.iumotionlabs.hefesto.desktop.execution;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.api.action.ActionDescriptor;
import org.iumotionlabs.hefesto.desktop.framework.FeatureRegistry;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;

import java.util.List;

public class ActionCatalogViewModel extends BaseViewModel {

    private final StringProperty searchQuery = new SimpleStringProperty("");
    private final ObservableList<ActionDescriptor> filteredActions = FXCollections.observableArrayList();
    private final List<ActionDescriptor> allActions;

    public ActionCatalogViewModel(FeatureRegistry registry) {
        this.allActions = registry.getAllActions();
        filteredActions.setAll(allActions);
        searchQuery.addListener((_, _, query) -> filter(query));
    }

    public StringProperty searchQueryProperty() { return searchQuery; }
    public ObservableList<ActionDescriptor> getFilteredActions() { return filteredActions; }

    private void filter(String query) {
        if (query == null || query.isBlank()) {
            filteredActions.setAll(allActions);
            return;
        }
        var lower = query.toLowerCase();
        filteredActions.setAll(
            allActions.stream()
                .filter(a -> a.labelKey().toLowerCase().contains(lower) ||
                             a.category().toLowerCase().contains(lower))
                .toList()
        );
    }
}
