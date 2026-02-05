package org.iumotionlabs.hefesto.desktop.shell;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

import java.util.HashMap;
import java.util.Map;

public class TabContainerView extends TabPane {

    private final Map<String, Tab> openTabs = new HashMap<>();

    public TabContainerView() {
        getStyleClass().add("tab-container");
        setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
    }

    public void openTab(NavigationContribution navItem, Node content) {
        var existing = openTabs.get(navItem.id());
        if (existing != null) {
            getSelectionModel().select(existing);
            return;
        }

        var tab = new Tab(I18nService.getInstance().t(navItem.labelKey()));
        tab.setContent(content);
        tab.setClosable(true);
        tab.setOnClosed(_ -> openTabs.remove(navItem.id()));

        openTabs.put(navItem.id(), tab);
        getTabs().add(tab);
        getSelectionModel().select(tab);
    }

    public void closeTab(String navItemId) {
        var tab = openTabs.remove(navItemId);
        if (tab != null) {
            getTabs().remove(tab);
        }
    }
}
