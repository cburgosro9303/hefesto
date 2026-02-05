package org.iumotionlabs.hefesto.desktop.feature.tools;

import org.iumotionlabs.hefesto.desktop.api.action.ActionDescriptor;
import org.iumotionlabs.hefesto.desktop.api.feature.*;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.api.widget.WidgetDescriptor;
import org.iumotionlabs.hefesto.desktop.feature.tools.view.ToolsView;

import java.util.List;

public class ToolsFeatureProvider implements FeatureProvider {

    @Override
    public String id() { return "tools"; }

    @Override
    public String displayName() { return "Utilities"; }

    @Override
    public String version() { return "1.0.0"; }

    @Override
    public FeatureCategory category() { return FeatureCategory.TOOLS; }

    @Override
    public List<NavigationContribution> navigationItems() {
        return List.of(
            new NavigationContribution("tools.toolkit", "tools.toolkit", "tools",
                "tools", 10, ToolsView.class)
        );
    }

    @Override
    public List<WidgetDescriptor> widgets() {
        return List.of();
    }

    @Override
    public List<ActionDescriptor> actions() {
        return List.of();
    }

    @Override
    public void initialize(FeatureContext context) {}

    @Override
    public void shutdown() {}

    @Override
    public int priority() { return 50; }
}
