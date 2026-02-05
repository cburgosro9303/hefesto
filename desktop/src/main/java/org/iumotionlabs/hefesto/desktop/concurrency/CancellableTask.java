package org.iumotionlabs.hefesto.desktop.concurrency;

import javafx.application.Platform;
import javafx.beans.property.*;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CancellableTask<T> {

    private final Supplier<T> work;
    private final Duration timeout;
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty statusMessage = new SimpleStringProperty();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Future<?> future;

    public CancellableTask(Supplier<T> work, Duration timeout) {
        this.work = work;
        this.timeout = timeout;
    }

    public BooleanProperty runningProperty() { return running; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty statusMessageProperty() { return statusMessage; }

    public void start(Consumer<T> onSuccess, Consumer<Throwable> onError) {
        cancelled.set(false);
        Platform.runLater(() -> running.set(true));

        future = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                T result = work.get();
                if (!cancelled.get()) {
                    Platform.runLater(() -> {
                        running.set(false);
                        onSuccess.accept(result);
                    });
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    Platform.runLater(() -> {
                        running.set(false);
                        onError.accept(e);
                    });
                }
            }
        });
    }

    public void cancel() {
        cancelled.set(true);
        if (future != null) {
            future.cancel(true);
        }
        Platform.runLater(() -> running.set(false));
    }

    public boolean isCancelled() { return cancelled.get(); }
}
