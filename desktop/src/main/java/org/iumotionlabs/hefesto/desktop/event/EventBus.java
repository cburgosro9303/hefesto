package org.iumotionlabs.hefesto.desktop.event;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {

    private static final Logger log = LogManager.getLogger(EventBus.class);
    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<?>, List<WeakReference<Consumer<?>>>> subscribers = new ConcurrentHashMap<>();

    private EventBus() {}

    public static EventBus getInstance() { return INSTANCE; }

    @SuppressWarnings("unchecked")
    public <T> EventSubscription subscribe(Class<T> eventType, Consumer<T> handler) {
        var list = subscribers.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>());
        var ref = new WeakReference<Consumer<?>>((Consumer<?>) handler);
        list.add(ref);

        return () -> list.remove(ref);
    }

    @SuppressWarnings("unchecked")
    public void publish(Object event) {
        var list = subscribers.get(event.getClass());
        if (list == null) return;

        list.removeIf(ref -> ref.get() == null);

        for (var ref : list) {
            var handler = (Consumer<Object>) ref.get();
            if (handler != null) {
                Platform.runLater(() -> {
                    try {
                        handler.accept(event);
                    } catch (Exception e) {
                        log.error("Error dispatching event {}", event.getClass().getSimpleName(), e);
                    }
                });
            }
        }
    }
}
