package org.iumotionlabs.hefesto.desktop.event;

@FunctionalInterface
public interface EventSubscription {
    void unsubscribe();
}
