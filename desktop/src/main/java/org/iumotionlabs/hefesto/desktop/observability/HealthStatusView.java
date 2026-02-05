package org.iumotionlabs.hefesto.desktop.observability;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.controls.StatusBadge;

public class HealthStatusView extends FlowPane {

    public HealthStatusView(HealthStatusViewModel viewModel) {
        getStyleClass().add("health-status-view");
        setHgap(12);
        setVgap(12);
        setPadding(new Insets(15));

        for (var module : viewModel.getModules()) {
            var card = createHealthCard(module);
            getChildren().add(card);
        }
    }

    private VBox createHealthCard(HealthStatusViewModel.ModuleHealth module) {
        var card = new VBox(6);
        card.getStyleClass().add("kpi-card");
        card.setPrefWidth(200);
        card.setPadding(new Insets(12));

        var name = new Label(module.moduleName());
        name.setStyle("-fx-font-weight: bold;");

        var badge = new StatusBadge(module.state().name(), switch (module.state()) {
            case HEALTHY -> StatusBadge.Severity.SUCCESS;
            case UNHEALTHY -> StatusBadge.Severity.CRITICAL;
            case UNKNOWN -> StatusBadge.Severity.WARNING;
        });

        var details = new Label(module.details());
        details.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

        card.getChildren().addAll(name, badge, details);
        return card;
    }
}
