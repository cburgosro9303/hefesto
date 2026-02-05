package org.iumotionlabs.hefesto.desktop.dashboard;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iumotionlabs.hefesto.desktop.api.widget.WidgetDescriptor;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

public class DashboardView extends ScrollPane {

    private static final Logger log = LogManager.getLogger(DashboardView.class);
    private final DashboardViewModel viewModel;
    private final FlowPane grid = new FlowPane();

    public DashboardView(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
        getStyleClass().add("dashboard-view");
        setFitToWidth(true);

        grid.getStyleClass().add("dashboard-grid");
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));

        setContent(grid);

        viewModel.getActiveWidgets().addListener(
            (javafx.collections.ListChangeListener<WidgetDescriptor>) _ -> rebuildWidgets()
        );
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        grid.getChildren().clear();
        if (viewModel.getActiveWidgets().isEmpty()) {
            grid.getChildren().add(new Label(I18nService.getInstance().t("dashboard.no.widgets")));
            return;
        }
        for (var descriptor : viewModel.getActiveWidgets()) {
            try {
                Node widgetView = (Node) descriptor.widgetViewClass().getDeclaredConstructor().newInstance();
                var container = new WidgetContainer(
                    descriptor.id(),
                    I18nService.getInstance().t(descriptor.titleKey()),
                    widgetView
                );
                double width = descriptor.defaultSize().columns() * 200 + (descriptor.defaultSize().columns() - 1) * 12;
                double height = descriptor.defaultSize().rows() * 180 + (descriptor.defaultSize().rows() - 1) * 12;
                container.setPrefSize(width, height);
                grid.getChildren().add(container);
            } catch (Exception e) {
                log.error("Failed to create widget: {}", descriptor.id(), e);
            }
        }
    }
}
