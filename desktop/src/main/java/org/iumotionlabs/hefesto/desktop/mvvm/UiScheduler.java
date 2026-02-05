package org.iumotionlabs.hefesto.desktop.mvvm;

@FunctionalInterface
public interface UiScheduler {
    void runOnUiThread(Runnable action);
}
