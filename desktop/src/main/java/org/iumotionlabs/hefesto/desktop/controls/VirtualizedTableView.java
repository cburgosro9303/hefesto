package org.iumotionlabs.hefesto.desktop.controls;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TableView;

import java.util.function.Predicate;

public class VirtualizedTableView<T> extends TableView<T> {

    private FilteredList<T> filteredData;
    private SortedList<T> sortedData;

    public VirtualizedTableView() {
        getStyleClass().add("virtualized-table");
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    public void setSourceItems(ObservableList<T> items) {
        filteredData = new FilteredList<>(items, _ -> true);
        sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(comparatorProperty());
        setItems(sortedData);
    }

    public void setFilter(Predicate<T> predicate) {
        if (filteredData != null) {
            filteredData.setPredicate(predicate);
        }
    }

    public void clearFilter() {
        if (filteredData != null) {
            filteredData.setPredicate(_ -> true);
        }
    }

    public String exportToCsv() {
        var sb = new StringBuilder();
        // Headers
        for (var col : getColumns()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(escCsv(col.getText()));
        }
        sb.append("\n");
        // Rows
        for (var item : getItems()) {
            var first = true;
            for (var col : getColumns()) {
                if (!first) sb.append(",");
                first = false;
                var cell = col.getCellData(item);
                sb.append(escCsv(cell != null ? cell.toString() : ""));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String escCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
