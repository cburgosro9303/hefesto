package org.iumotionlabs.hefesto.desktop.concurrency;

import javafx.application.Platform;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

public final class FxScheduler {

    private FxScheduler() {}

    public static Runnable debounce(Runnable action, long delayMs) {
        var pending = new AtomicReference<TimerTask>();
        var timer = new Timer(true);

        return () -> {
            var old = pending.get();
            if (old != null) old.cancel();

            var task = new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(action);
                }
            };
            pending.set(task);
            timer.schedule(task, delayMs);
        };
    }

    public static Runnable throttle(Runnable action, long intervalMs) {
        var lastRun = new AtomicReference<>(0L);
        return () -> {
            long now = System.currentTimeMillis();
            if (now - lastRun.get() >= intervalMs) {
                lastRun.set(now);
                Platform.runLater(action);
            }
        };
    }
}
