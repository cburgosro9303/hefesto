package org.iumotionlabs.hefesto.desktop.mvvm;

import javafx.beans.property.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.iumotionlabs.hefesto.desktop.concurrency.HefestoExecutors;

public class AsyncCommand<T> {

    private final Supplier<T> task;
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final ObjectProperty<T> result = new SimpleObjectProperty<>();
    private final StringProperty error = new SimpleStringProperty();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final UiScheduler scheduler;

    public AsyncCommand(Supplier<T> task, UiScheduler scheduler) {
        this.task = task;
        this.scheduler = scheduler;
    }

    public BooleanProperty runningProperty() { return running; }
    public ObjectProperty<T> resultProperty() { return result; }
    public StringProperty errorProperty() { return error; }

    public void execute() {
        if (running.get()) return;
        cancelled.set(false);
        scheduler.runOnUiThread(() -> {
            running.set(true);
            error.set(null);
        });

        HefestoExecutors.runAsync(
            task,
            value -> {
                if (!cancelled.get()) {
                    scheduler.runOnUiThread(() -> {
                        result.set(value);
                        running.set(false);
                    });
                }
            },
            ex -> scheduler.runOnUiThread(() -> {
                error.set(ex.getMessage());
                running.set(false);
            })
        );
    }

    public void cancel() {
        cancelled.set(true);
        scheduler.runOnUiThread(() -> running.set(false));
    }
}
