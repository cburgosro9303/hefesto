package org.iumotionlabs.hefesto.desktop.shell;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

import java.util.function.Consumer;

public class SidebarView extends VBox {

    private Consumer<NavigationContribution> onNavigate;
    private Button selectedButton;

    public SidebarView() {
        getStyleClass().add("sidebar");
        setPrefWidth(220);
        setMinWidth(200);
        setPadding(new Insets(10));
        setSpacing(4);

        var title = new Label("HEFESTO");
        title.getStyleClass().add("sidebar-title");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        title.setPadding(new Insets(10, 0, 15, 0));

        getChildren().addAll(title, new Separator());
    }

    public void setOnNavigate(Consumer<NavigationContribution> handler) {
        this.onNavigate = handler;
    }

    public void setNavigationItems(java.util.List<NavigationContribution> items) {
        // Remove old nav buttons (keep title + separator)
        if (getChildren().size() > 2) {
            getChildren().remove(2, getChildren().size());
        }

        String currentParent = null;
        for (var item : items) {
            if (item.parentId() != null && !item.parentId().equals(currentParent)) {
                currentParent = item.parentId();
                var groupLabel = new Label(I18nService.getInstance().t(item.parentId()).toUpperCase());
                groupLabel.getStyleClass().add("sidebar-group-label");
                groupLabel.setPadding(new Insets(12, 0, 4, 8));
                getChildren().add(groupLabel);
            }

            var btn = new Button(I18nService.getInstance().t(item.labelKey()));
            btn.getStyleClass().add("sidebar-nav-button");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setGraphicTextGap(8);

            btn.setOnAction(_ -> {
                if (selectedButton != null) selectedButton.getStyleClass().remove("selected");
                btn.getStyleClass().add("selected");
                selectedButton = btn;
                if (onNavigate != null) onNavigate.accept(item);
            });
            getChildren().add(btn);
        }

        var spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);
    }

    public void selectFirst() {
        for (var node : getChildren()) {
            if (node instanceof Button btn && btn.getStyleClass().contains("sidebar-nav-button")) {
                btn.fire();
                break;
            }
        }
    }
}
