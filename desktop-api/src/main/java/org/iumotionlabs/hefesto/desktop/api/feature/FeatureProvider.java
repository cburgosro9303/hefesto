package org.iumotionlabs.hefesto.desktop.api.feature;

import org.iumotionlabs.hefesto.desktop.api.action.ActionDescriptor;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.api.widget.WidgetDescriptor;

import java.util.List;

public interface FeatureProvider {

    String id();

    String displayName();

    String version();

    FeatureCategory category();

    List<NavigationContribution> navigationItems();

    List<WidgetDescriptor> widgets();

    List<ActionDescriptor> actions();

    void initialize(FeatureContext context);

    void shutdown();

    default int priority() { return 0; }
}
