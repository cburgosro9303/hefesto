package org.iumotionlabs.hefesto.desktop.api.widget;

public enum WidgetSize {
    SMALL(1, 1),
    MEDIUM(2, 1),
    LARGE(2, 2),
    WIDE(3, 1),
    FULL(3, 2);

    private final int columns;
    private final int rows;

    WidgetSize(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public int columns() { return columns; }
    public int rows() { return rows; }
}
