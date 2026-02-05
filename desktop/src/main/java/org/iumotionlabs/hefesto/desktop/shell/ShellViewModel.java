package org.iumotionlabs.hefesto.desktop.shell;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.framework.FeatureRegistry;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;

public class ShellViewModel extends BaseViewModel {

    private final FeatureRegistry featureRegistry;
    private final ObservableList<NavigationContribution> navigationItems = FXCollections.observableArrayList();
    private final ObjectProperty<NavigationContribution> selectedNavItem = new SimpleObjectProperty<>();
    private final StringProperty statusText = new SimpleStringProperty("Ready");
    private final IntegerProperty runningTaskCount = new SimpleIntegerProperty(0);
    private final BooleanProperty commandPaletteVisible = new SimpleBooleanProperty(false);

    public ShellViewModel(FeatureRegistry featureRegistry) {
        this.featureRegistry = featureRegistry;
        navigationItems.setAll(featureRegistry.getAllNavigationItems());
    }

    public ObservableList<NavigationContribution> getNavigationItems() { return navigationItems; }
    public ObjectProperty<NavigationContribution> selectedNavItemProperty() { return selectedNavItem; }
    public StringProperty statusTextProperty() { return statusText; }
    public IntegerProperty runningTaskCountProperty() { return runningTaskCount; }
    public BooleanProperty commandPaletteVisibleProperty() { return commandPaletteVisible; }

    public void navigateTo(NavigationContribution item) {
        selectedNavItem.set(item);
    }

    public void toggleCommandPalette() {
        commandPaletteVisible.set(!commandPaletteVisible.get());
    }

    public FeatureRegistry getFeatureRegistry() { return featureRegistry; }
}
