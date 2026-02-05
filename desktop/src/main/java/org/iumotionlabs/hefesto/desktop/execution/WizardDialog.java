package org.iumotionlabs.hefesto.desktop.execution;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public class WizardDialog extends Stage {

    private final List<WizardStep> steps;
    private int currentStep = 0;
    private final StackPane contentArea = new StackPane();
    private final Label stepLabel = new Label();
    private final Button prevBtn = new Button("< Back");
    private final Button nextBtn = new Button("Next >");
    private final Button finishBtn = new Button("Finish");
    private boolean completed = false;

    public WizardDialog(String title, List<WizardStep> steps) {
        this.steps = steps;
        setTitle(title);
        initModality(Modality.APPLICATION_MODAL);

        stepLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        prevBtn.setOnAction(_ -> navigateTo(currentStep - 1));
        nextBtn.setOnAction(_ -> navigateTo(currentStep + 1));
        finishBtn.setOnAction(_ -> { completed = true; close(); });

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var buttonBar = new HBox(10, prevBtn, spacer, nextBtn, finishBtn);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        var root = new VBox(10, stepLabel, contentArea, buttonBar);
        root.setPadding(new Insets(20));
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        setScene(new Scene(root, 500, 400));
        navigateTo(0);
    }

    private void navigateTo(int index) {
        if (index < 0 || index >= steps.size()) return;

        currentStep = index;
        var step = steps.get(index);
        stepLabel.setText("Step " + (index + 1) + "/" + steps.size() + ": " + step.title());
        contentArea.getChildren().setAll(step.content());

        prevBtn.setDisable(index == 0);
        nextBtn.setVisible(index < steps.size() - 1);
        finishBtn.setVisible(index == steps.size() - 1);
    }

    public boolean isCompleted() { return completed; }
}
