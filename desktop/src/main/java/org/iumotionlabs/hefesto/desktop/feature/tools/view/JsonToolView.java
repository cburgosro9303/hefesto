package org.iumotionlabs.hefesto.desktop.feature.tools.view;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.controls.StatusBadge;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

public class JsonToolView extends VBox {

    private final TextArea inputArea = new TextArea();
    private final TextArea outputArea = new TextArea();
    private final StatusBadge validationBadge = new StatusBadge("", StatusBadge.Severity.INFO);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectMapper prettyMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT).build();

    public JsonToolView() {
        var i18n = I18nService.getInstance();
        setSpacing(8);
        setPadding(new Insets(10));
        getStyleClass().add("tool-view");

        // Input
        var inputLabel = new Label(i18n.t("tools.input"));
        inputLabel.setStyle("-fx-font-weight: bold;");
        inputArea.setPromptText("Enter JSON...");
        inputArea.setWrapText(true);
        inputArea.getStyleClass().add("tool-textarea");
        inputArea.setStyle("-fx-font-family: 'Monospaced';");
        VBox.setVgrow(inputArea, Priority.ALWAYS);

        // Toolbar
        var formatBtn = new Button(i18n.t("tools.json.format"));
        formatBtn.setOnAction(_ -> formatJson());

        var compactBtn = new Button(i18n.t("tools.json.compact"));
        compactBtn.setOnAction(_ -> compactJson());

        var validateBtn = new Button(i18n.t("tools.json.validate"));
        validateBtn.setOnAction(_ -> validateJson());

        var clearBtn = new Button(i18n.t("tools.clear"));
        clearBtn.setOnAction(_ -> {
            inputArea.clear();
            outputArea.clear();
            validationBadge.setText("");
        });

        var copyBtn = new Button(i18n.t("tools.copy"));
        copyBtn.setOnAction(_ -> {
            var text = outputArea.getText();
            if (text != null && !text.isEmpty()) {
                var content = new ClipboardContent();
                content.putString(text);
                Clipboard.getSystemClipboard().setContent(content);
            }
        });

        validationBadge.setVisible(false);
        validationBadge.managedProperty().bind(validationBadge.visibleProperty());

        var toolbar = new HBox(8, formatBtn, compactBtn, validateBtn, validationBadge,
            new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }},
            clearBtn, copyBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Output
        var outputLabel = new Label(i18n.t("tools.output"));
        outputLabel.setStyle("-fx-font-weight: bold;");
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.getStyleClass().add("tool-textarea");
        outputArea.setStyle("-fx-font-family: 'Monospaced';");
        VBox.setVgrow(outputArea, Priority.ALWAYS);

        getChildren().addAll(inputLabel, inputArea, toolbar, outputLabel, outputArea);
    }

    private void formatJson() {
        var input = inputArea.getText();
        if (input == null || input.isBlank()) return;

        try {
            var tree = mapper.readTree(input);
            outputArea.setText(prettyMapper.writeValueAsString(tree));
            showValid();
        } catch (Exception e) {
            outputArea.setText("Error: " + e.getMessage());
            showInvalid();
        }
    }

    private void compactJson() {
        var input = inputArea.getText();
        if (input == null || input.isBlank()) return;

        try {
            var tree = mapper.readTree(input);
            outputArea.setText(mapper.writeValueAsString(tree));
            showValid();
        } catch (Exception e) {
            outputArea.setText("Error: " + e.getMessage());
            showInvalid();
        }
    }

    private void validateJson() {
        var input = inputArea.getText();
        if (input == null || input.isBlank()) return;

        try {
            mapper.readTree(input);
            outputArea.setText(input);
            showValid();
        } catch (Exception e) {
            outputArea.setText("Validation error: " + e.getMessage());
            showInvalid();
        }
    }

    private void showValid() {
        var i18n = I18nService.getInstance();
        validationBadge.setText(i18n.t("tools.json.valid"));
        validationBadge.setSeverity(StatusBadge.Severity.SUCCESS);
        validationBadge.setVisible(true);
    }

    private void showInvalid() {
        var i18n = I18nService.getInstance();
        validationBadge.setText(i18n.t("tools.json.invalid"));
        validationBadge.setSeverity(StatusBadge.Severity.CRITICAL);
        validationBadge.setVisible(true);
    }
}
