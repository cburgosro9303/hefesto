package org.iumotionlabs.hefesto.desktop.chart;

import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

public class TimeSeriesChart extends LineChart<Number, Number> {

    private final int maxDataPoints;
    private int xCounter = 0;

    public TimeSeriesChart(String title, String yAxisLabel, int maxDataPoints) {
        super(new NumberAxis(), new NumberAxis());
        this.maxDataPoints = maxDataPoints;

        setTitle(title);
        setAnimated(false);
        setCreateSymbols(false);
        setLegendVisible(true);

        var xAxis = (NumberAxis) getXAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(true);
        xAxis.setTickLabelsVisible(false);
        xAxis.setLabel("Time");

        var yAxis = (NumberAxis) getYAxis();
        yAxis.setLabel(yAxisLabel);
        yAxis.setAutoRanging(true);
    }

    public XYChart.Series<Number, Number> addSeries(String name) {
        var series = new XYChart.Series<Number, Number>();
        series.setName(name);
        getData().add(series);
        return series;
    }

    public void addDataPoint(XYChart.Series<Number, Number> series, double value) {
        Platform.runLater(() -> {
            series.getData().add(new XYChart.Data<>(xCounter++, value));
            if (series.getData().size() > maxDataPoints) {
                series.getData().removeFirst();
            }
        });
    }

    public void clear() {
        getData().forEach(s -> s.getData().clear());
        xCounter = 0;
    }
}
