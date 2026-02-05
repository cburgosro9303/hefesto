package org.iumotionlabs.hefesto.desktop.framework;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iumotionlabs.hefesto.desktop.ServiceLocator;
import org.iumotionlabs.hefesto.desktop.api.feature.FeatureContext;
import org.iumotionlabs.hefesto.desktop.api.feature.FeatureProvider;
import org.iumotionlabs.hefesto.desktop.api.preferences.PreferencesAccessor;
import org.iumotionlabs.hefesto.desktop.event.EventBus;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

public final class FeatureLoader {

    private static final Logger log = LogManager.getLogger(FeatureLoader.class);

    public List<FeatureProvider> discover() {
        return ServiceLoader.load(FeatureProvider.class).stream()
            .map(ServiceLoader.Provider::get)
            .sorted(Comparator.comparingInt(FeatureProvider::priority).reversed())
            .toList();
    }

    public FeatureContext createContext(FeatureProvider provider) {
        return new FeatureContext() {
            @Override
            public void publishEvent(Object event) {
                EventBus.getInstance().publish(event);
            }

            @Override
            public String translate(String key, Object... args) {
                return I18nService.getInstance().translate(key, args);
            }

            @Override
            public PreferencesAccessor preferences() {
                return ServiceLocator.get(PreferencesAccessor.class);
            }

            @Override
            public <T> T getService(Class<T> serviceType) {
                return ServiceLocator.get(serviceType);
            }
        };
    }
}
