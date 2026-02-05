package org.iumotionlabs.hefesto.desktop.feature.tools.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Base64ToolView extends VBox {

    private final TextArea inputArea = new TextArea();
    private final TextArea outputArea = new TextArea();
    private final Label errorLabel = new Label();

    public Base64ToolView() {
        var i18n = I18nService.getInstance();
        setSpacing(8);
        setPadding(new Insets(10));
        getStyleClass().add("tool-view");

        // Input
        var inputLabel = new Label(i18n.t("tools.input"));
        inputLabel.setStyle("-fx-font-weight: bold;");
        inputArea.setPromptText("Enter text to encode/decode...");
        inputArea.setWrapText(true);
        inputArea.getStyleClass().add("tool-textarea");
        VBox.setVgrow(inputArea, Priority.ALWAYS);

        // Toolbar
        var urlSafe = new CheckBox(i18n.t("tools.base64.url.safe"));
        var mime = new CheckBox(i18n.t("tools.base64.mime"));

        var encodeBtn = new Button(i18n.t("tools.base64.encode"));
        encodeBtn.setOnAction(_ -> encode(urlSafe.isSelected(), mime.isSelected()));

        var decodeBtn = new Button(i18n.t("tools.base64.decode"));
        decodeBtn.setOnAction(_ -> decode(urlSafe.isSelected()));

        var clearBtn = new Button(i18n.t("tools.clear"));
        clearBtn.setOnAction(_ -> {
            inputArea.clear();
            outputArea.clear();
            errorLabel.setText("");
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

        var toolbar = new HBox(8, encodeBtn, decodeBtn, urlSafe, mime,
            new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }},
            clearBtn, copyBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Output
        var outputLabel = new Label(i18n.t("tools.output"));
        outputLabel.setStyle("-fx-font-weight: bold;");
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.getStyleClass().add("tool-textarea");
        VBox.setVgrow(outputArea, Priority.ALWAYS);

        // Error
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
        errorLabel.setWrapText(true);

        getChildren().addAll(inputLabel, inputArea, toolbar, outputLabel, outputArea, errorLabel);
    }

    private void encode(boolean urlSafe, boolean useMime) {
        errorLabel.setText("");
        var input = inputArea.getText();
        if (input == null || input.isEmpty()) return;

        try {
            byte[] data = input.getBytes(StandardCharsets.UTF_8);
            Base64.Encoder encoder;
            if (useMime) {
                encoder = Base64.getMimeEncoder();
            } else if (urlSafe) {
                encoder = Base64.getUrlEncoder();
            } else {
                encoder = Base64.getEncoder();
            }
            outputArea.setText(encoder.encodeToString(data));
        } catch (Exception e) {
            errorLabel.setText("Encode error: " + e.getMessage());
        }
    }

    private void decode(boolean urlSafe) {
        errorLabel.setText("");
        var input = inputArea.getText();
        if (input == null || input.isEmpty()) return;

        try {
            Base64.Decoder decoder = urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder();
            byte[] decoded = decoder.decode(input.trim());
            outputArea.setText(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            errorLabel.setText("Invalid Base64 input: " + e.getMessage());
        } catch (Exception e) {
            errorLabel.setText("Decode error: " + e.getMessage());
        }
    }
}
