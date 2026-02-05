package org.iumotionlabs.hefesto.desktop.feature.portinfo;

import org.iumotionlabs.hefesto.desktop.api.action.ActionDescriptor;
import org.iumotionlabs.hefesto.desktop.api.action.ActionType;
import org.iumotionlabs.hefesto.desktop.api.feature.*;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.api.widget.WidgetDescriptor;
import org.iumotionlabs.hefesto.desktop.api.widget.WidgetSize;
import org.iumotionlabs.hefesto.desktop.feature.portinfo.view.NetworkExplorerView;
import org.iumotionlabs.hefesto.desktop.feature.portinfo.view.PortOverviewWidget;

import java.time.Duration;
import java.util.List;

public class PortInfoFeatureProvider implements FeatureProvider {

    @Override
    public String id() { return "portinfo"; }

    @Override
    public String displayName() { return "Port Information"; }

    @Override
    public String version() { return "1.0.0"; }

    @Override
    public FeatureCategory category() { return FeatureCategory.NETWORK; }

    @Override
    public List<NavigationContribution> navigationItems() {
        return List.of(
            new NavigationContribution("portinfo.explorer", "portinfo.network.explorer", "network",
                "portinfo", 10, NetworkExplorerView.class)
        );
    }

    @Override
    public List<WidgetDescriptor> widgets() {
        return List.of(
            new WidgetDescriptor("portinfo.overview", "portinfo.overview", "portinfo",
                WidgetSize.MEDIUM, PortOverviewWidget.class, true, Duration.ofSeconds(10))
        );
    }

    @Override
    public List<ActionDescriptor> actions() {
        return List.of(
            new ActionDescriptor("portinfo.scan", "portinfo.scan.ports", "portinfo.scan.ports",
                "scan", "Network", ActionType.LONG_RUNNING, Void.class),
            new ActionDescriptor("portinfo.healthcheck", "portinfo.health.check", "portinfo.health.check",
                "health", "Network", ActionType.INSTANT, Void.class)
        );
    }

    @Override
    public void initialize(FeatureContext context) {
        // PortInfo services already initialized via ServiceLocator
    }

    @Override
    public void shutdown() {
        // No cleanup needed
    }

    @Override
    public int priority() { return 10; }
}
