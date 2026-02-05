package org.iumotionlabs.hefesto.desktop.dashboard;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public class WidgetContainer extends VBox {

    private final String widgetId;

    public WidgetContainer(String widgetId, String title, Node content) {
        this.widgetId = widgetId;
        getStyleClass().add("widget-container");

        var titleLabel = new Label(title);
        titleLabel.getStyleClass().add("widget-title");

        var refreshBtn = new Button("\u21BB");
        refreshBtn.getStyleClass().add("widget-refresh-btn");
        refreshBtn.setOnAction(_ -> {
            if (content instanceof Refreshable r) r.refresh();
        });

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var titleBar = new HBox(8, titleLabel, spacer, refreshBtn);
        titleBar.getStyleClass().add("widget-title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 4, 0, 0));

        VBox.setVgrow(content, Priority.ALWAYS);
        getChildren().addAll(titleBar, content);
    }

    public String getWidgetId() { return widgetId; }

    public interface Refreshable {
        void refresh();
    }
}
