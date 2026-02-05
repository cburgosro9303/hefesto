package org.iumotionlabs.hefesto.desktop.framework;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iumotionlabs.hefesto.desktop.api.action.ActionDescriptor;
import org.iumotionlabs.hefesto.desktop.api.feature.FeatureProvider;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.api.widget.WidgetDescriptor;

import java.util.*;

public final class FeatureRegistry {

    private static final Logger log = LogManager.getLogger(FeatureRegistry.class);
    private final List<FeatureProvider> providers = new ArrayList<>();
    private final FeatureLoader loader = new FeatureLoader();

    public void discoverAndInitialize() {
        var discovered = loader.discover();
        log.info("Discovered {} feature providers", discovered.size());

        for (var provider : discovered) {
            try {
                provider.initialize(loader.createContext(provider));
                providers.add(provider);
                log.info("Initialized feature: {} v{}", provider.displayName(), provider.version());
            } catch (Exception e) {
                log.error("Failed to initialize feature: {}", provider.id(), e);
            }
        }
    }

    public List<FeatureProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    public List<NavigationContribution> getAllNavigationItems() {
        return providers.stream()
            .flatMap(p -> p.navigationItems().stream())
            .sorted(Comparator.comparingInt(NavigationContribution::order))
            .toList();
    }

    public List<WidgetDescriptor> getAllWidgets() {
        return providers.stream()
            .flatMap(p -> p.widgets().stream())
            .toList();
    }

    public List<ActionDescriptor> getAllActions() {
        return providers.stream()
            .flatMap(p -> p.actions().stream())
            .toList();
    }

    public Optional<FeatureProvider> findById(String id) {
        return providers.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public void shutdownAll() {
        for (var provider : providers) {
            try {
                provider.shutdown();
            } catch (Exception e) {
                log.error("Error shutting down feature: {}", provider.id(), e);
            }
        }
        providers.clear();
    }
}
