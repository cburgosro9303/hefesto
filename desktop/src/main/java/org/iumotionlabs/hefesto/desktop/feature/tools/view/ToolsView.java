package org.iumotionlabs.hefesto.desktop.feature.tools.view;

import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

public class ToolsView extends VBox {

    public ToolsView() {
        var i18n = I18nService.getInstance();
        getStyleClass().add("tool-view");

        var tabPane = new TabPane();

        var base64Tab = new javafx.scene.control.Tab(i18n.t("tools.base64"), new Base64ToolView());
        base64Tab.setClosable(false);

        var jsonTab = new javafx.scene.control.Tab(i18n.t("tools.json"), new JsonToolView());
        jsonTab.setClosable(false);

        tabPane.getTabs().addAll(base64Tab, jsonTab);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().add(tabPane);
    }
}
