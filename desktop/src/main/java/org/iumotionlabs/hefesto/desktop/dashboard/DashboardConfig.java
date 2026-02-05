package org.iumotionlabs.hefesto.desktop.dashboard;

import java.util.ArrayList;
import java.util.List;

public class DashboardConfig {

    public record WidgetLayout(String widgetId, int column, int row, int colSpan, int rowSpan) {}

    private final List<WidgetLayout> layouts = new ArrayList<>();

    public List<WidgetLayout> getLayouts() { return layouts; }

    public void addLayout(WidgetLayout layout) { layouts.add(layout); }

    public void removeLayout(String widgetId) {
        layouts.removeIf(l -> l.widgetId().equals(widgetId));
    }
}
