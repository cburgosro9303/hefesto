package org.iumotionlabs.hefesto.desktop.api.widget;

import java.time.Duration;

public record WidgetDescriptor(
    String id,
    String titleKey,
    String featureId,
    WidgetSize defaultSize,
    Class<?> widgetViewClass,
    boolean supportsRefresh,
    Duration autoRefreshInterval
) {}
