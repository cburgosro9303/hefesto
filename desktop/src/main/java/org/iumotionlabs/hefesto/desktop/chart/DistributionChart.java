package org.iumotionlabs.hefesto.desktop.chart;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;

import java.util.Map;

public class DistributionChart extends PieChart {

    public DistributionChart(String title) {
        setTitle(title);
        setAnimated(true);
        setLegendVisible(true);
        setLabelsVisible(true);
    }

    public void setDistribution(Map<String, Number> data) {
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        data.forEach((name, value) -> pieData.add(new PieChart.Data(name, value.doubleValue())));
        setData(pieData);
    }

    public void clear() {
        getData().clear();
    }
}
