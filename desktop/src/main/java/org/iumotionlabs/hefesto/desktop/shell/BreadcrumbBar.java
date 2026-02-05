package org.iumotionlabs.hefesto.desktop.shell;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.List;

public class BreadcrumbBar extends HBox {

    public BreadcrumbBar() {
        getStyleClass().add("breadcrumb-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(4);
        setPadding(new Insets(4, 10, 4, 10));
    }

    public void setPath(List<String> segments) {
        getChildren().clear();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                var separator = new Label(">");
                separator.getStyleClass().add("breadcrumb-separator");
                getChildren().add(separator);
            }
            var label = new Label(segments.get(i));
            label.getStyleClass().add("breadcrumb-segment");
            if (i == segments.size() - 1) {
                label.getStyleClass().add("breadcrumb-current");
            }
            getChildren().add(label);
        }
    }
}
