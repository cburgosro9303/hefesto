package org.iumotionlabs.hefesto.desktop.api.action;

public interface ActionMonitor {

    void updateProgress(double progress, String message);

    void log(String message);

    boolean isCancelled();
}
