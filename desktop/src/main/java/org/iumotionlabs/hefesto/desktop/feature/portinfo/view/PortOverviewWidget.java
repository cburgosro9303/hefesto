package org.iumotionlabs.hefesto.desktop.feature.portinfo.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.iumotionlabs.hefesto.desktop.controls.KpiCard;
import org.iumotionlabs.hefesto.desktop.dashboard.WidgetContainer;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

public class PortOverviewWidget extends VBox implements WidgetContainer.Refreshable {

    private final PortOverviewWidgetViewModel viewModel = new PortOverviewWidgetViewModel();
    private final KpiCard listeningCard;
    private final KpiCard exposedCard;
    private final KpiCard securityCard;

    public PortOverviewWidget() {
        var i18n = I18nService.getInstance();
        setSpacing(8);
        setPadding(new Insets(8));
        setAlignment(Pos.CENTER);

        listeningCard = new KpiCard(i18n.t("portinfo.total.listening"), "--");
        exposedCard = new KpiCard(i18n.t("portinfo.exposed"), "--");
        exposedCard.setValueColor("#e67e22");
        securityCard = new KpiCard(i18n.t("portinfo.security.issues"), "--");
        securityCard.setValueColor("#e74c3c");

        viewModel.totalListeningProperty().addListener((_, _, val) -> listeningCard.setValue(val.toString()));
        viewModel.exposedCountProperty().addListener((_, _, val) -> exposedCard.setValue(val.toString()));
        viewModel.securityIssueCountProperty().addListener((_, _, val) -> securityCard.setValue(val.toString()));

        var cardsRow = new HBox(8, listeningCard, exposedCard, securityCard);
        cardsRow.setAlignment(Pos.CENTER);
        getChildren().add(cardsRow);

        refresh();
    }

    @Override
    public void refresh() {
        viewModel.refresh();
    }
}
