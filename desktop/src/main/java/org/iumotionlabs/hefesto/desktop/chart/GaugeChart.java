package org.iumotionlabs.hefesto.desktop.chart;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class GaugeChart extends StackPane {

    private final Canvas canvas;
    private final DoubleProperty value = new SimpleDoubleProperty(0);
    private final DoubleProperty maxValue = new SimpleDoubleProperty(100);
    private final StringProperty label = new SimpleStringProperty("Value");

    public GaugeChart(double size) {
        canvas = new Canvas(size, size);
        getChildren().add(canvas);

        value.addListener((_, _, _) -> draw());
        maxValue.addListener((_, _, _) -> draw());
        draw();
    }

    public DoubleProperty valueProperty() { return value; }
    public DoubleProperty maxValueProperty() { return maxValue; }
    public StringProperty labelProperty() { return label; }

    public void setValue(double val) { value.set(val); }

    private void draw() {
        var gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);

        double centerX = w / 2;
        double centerY = h / 2;
        double radius = Math.min(w, h) / 2 - 10;
        double arcAngle = 240;
        double startAngle = 150;

        // Background arc
        gc.setStroke(Color.gray(0.3));
        gc.setLineWidth(12);
        gc.strokeArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
            startAngle, arcAngle, javafx.scene.shape.ArcType.OPEN);

        // Value arc
        double pct = Math.min(value.get() / maxValue.get(), 1.0);
        Color arcColor = pct < 0.6 ? Color.web("#2ecc71") :
                         pct < 0.8 ? Color.web("#f1c40f") : Color.web("#e74c3c");
        gc.setStroke(arcColor);
        gc.setLineWidth(12);
        gc.strokeArc(centerX - radius, centerY - radius, radius * 2, radius * 2,
            startAngle, arcAngle * pct, javafx.scene.shape.ArcType.OPEN);

        // Value text
        gc.setFill(Color.web("#cdd6f4"));
        gc.setFont(Font.font("System", javafx.scene.text.FontWeight.BOLD, radius * 0.5));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.format("%.0f%%", value.get()), centerX, centerY);

        // Label
        gc.setFont(Font.font("System", radius * 0.22));
        gc.fillText(label.get(), centerX, centerY + radius * 0.35);
    }
}
