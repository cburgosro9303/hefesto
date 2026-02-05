package org.iumotionlabs.hefesto.desktop;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iumotionlabs.hefesto.desktop.api.preferences.PreferencesAccessor;
import org.iumotionlabs.hefesto.desktop.preferences.PreferencesService;
import org.iumotionlabs.hefesto.feature.portinfo.service.*;
import org.iumotionlabs.hefesto.feature.procwatch.service.ProcessMonitorService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceLocator {

    private static final Logger log = LogManager.getLogger(ServiceLocator.class);
    private static final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    private ServiceLocator() {}

    public static void initialize() {
        if (initialized) return;
        synchronized (ServiceLocator.class) {
            if (initialized) return;
            log.info("Initializing service locator");

            register(PortInfoService.class, new PortInfoService());
            register(ProcessMonitorService.class, new ProcessMonitorService());
            register(HealthCheckService.class, new HealthCheckService());
            register(SecurityAnalysisService.class, new SecurityAnalysisService());
            register(DockerService.class, new DockerService());
            register(ProcessEnrichmentService.class, new ProcessEnrichmentService());
            register(ServiceRegistry.class, new ServiceRegistry());
            register(PreferencesAccessor.class, PreferencesService.getInstance());

            initialized = true;
            log.info("Service locator initialized with {} services", services.size());
        }
    }

    public static <T> void register(Class<T> type, T instance) {
        services.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        var service = (T) services.get(type);
        if (service == null) {
            throw new IllegalStateException("Service not registered: " + type.getName());
        }
        return service;
    }

    public static void shutdown() {
        log.info("Shutting down service locator");
        var monitorService = services.get(ProcessMonitorService.class);
        if (monitorService instanceof ProcessMonitorService pms) {
            pms.shutdown();
        }
        services.clear();
        initialized = false;
    }
}
