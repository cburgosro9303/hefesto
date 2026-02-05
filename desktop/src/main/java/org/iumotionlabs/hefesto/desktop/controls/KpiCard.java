package org.iumotionlabs.hefesto.desktop.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class KpiCard extends VBox {

    private final StringProperty value = new SimpleStringProperty("--");
    private final StringProperty label = new SimpleStringProperty();
    private final Label valueLabel = new Label();
    private final Label textLabel = new Label();

    public KpiCard() {
        getStyleClass().add("kpi-card");
        setAlignment(Pos.CENTER);
        setSpacing(4);
        setPrefWidth(160);
        setPrefHeight(100);

        valueLabel.getStyleClass().add("kpi-card-value");
        valueLabel.textProperty().bind(value);

        textLabel.getStyleClass().add("kpi-card-label");
        textLabel.textProperty().bind(label);

        getChildren().addAll(valueLabel, textLabel);
    }

    public KpiCard(String labelText, String initialValue) {
        this();
        label.set(labelText);
        value.set(initialValue);
    }

    public StringProperty valueProperty() { return value; }
    public StringProperty labelProperty() { return label; }

    public void setValue(String val) { value.set(val); }
    public void setLabel(String text) { label.set(text); }

    public void setValueColor(String cssColor) {
        valueLabel.setStyle("-fx-text-fill: " + cssColor + ";");
    }
}
