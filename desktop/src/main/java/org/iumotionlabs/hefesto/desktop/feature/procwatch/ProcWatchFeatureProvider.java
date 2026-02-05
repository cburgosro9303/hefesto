package org.iumotionlabs.hefesto.desktop.feature.procwatch;

import org.iumotionlabs.hefesto.desktop.api.action.ActionDescriptor;
import org.iumotionlabs.hefesto.desktop.api.feature.*;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.api.widget.WidgetDescriptor;
import org.iumotionlabs.hefesto.desktop.api.widget.WidgetSize;
import org.iumotionlabs.hefesto.desktop.feature.procwatch.view.ProcessExplorerView;
import org.iumotionlabs.hefesto.desktop.feature.procwatch.view.ProcessMonitorView;
import org.iumotionlabs.hefesto.desktop.feature.procwatch.view.TopProcessesWidget;

import java.time.Duration;
import java.util.List;

public class ProcWatchFeatureProvider implements FeatureProvider {

    @Override
    public String id() { return "procwatch"; }

    @Override
    public String displayName() { return "Process Monitor"; }

    @Override
    public String version() { return "1.0.0"; }

    @Override
    public FeatureCategory category() { return FeatureCategory.SYSTEM; }

    @Override
    public List<NavigationContribution> navigationItems() {
        return List.of(
            new NavigationContribution("procwatch.explorer", "procwatch.explorer", "process",
                "procwatch", 10, ProcessExplorerView.class),
            new NavigationContribution("procwatch.monitor", "procwatch.monitor", "process",
                "procwatch", 20, ProcessMonitorView.class)
        );
    }

    @Override
    public List<WidgetDescriptor> widgets() {
        return List.of(
            new WidgetDescriptor("procwatch.top", "procwatch.top.processes", "procwatch",
                WidgetSize.LARGE, TopProcessesWidget.class, true, Duration.ofSeconds(5))
        );
    }

    @Override
    public List<ActionDescriptor> actions() {
        return List.of();
    }

    @Override
    public void initialize(FeatureContext context) {
        // ProcessMonitorService already initialized via ServiceLocator
    }

    @Override
    public void shutdown() {
        // Cleanup handled by ServiceLocator
    }

    @Override
    public int priority() { return 5; }
}
