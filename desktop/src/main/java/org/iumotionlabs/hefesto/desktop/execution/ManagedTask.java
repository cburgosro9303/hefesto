package org.iumotionlabs.hefesto.desktop.execution;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.api.action.ActionHandler;
import org.iumotionlabs.hefesto.desktop.api.action.ActionMonitor;
import org.iumotionlabs.hefesto.desktop.api.action.ActionResult;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ManagedTask implements ActionMonitor {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

    private final String id;
    private final String name;
    private final ActionHandler handler;
    private final Map<String, Object> params;
    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.PENDING);
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty progressMessage = new SimpleStringProperty();
    private final ObservableList<String> logs = FXCollections.observableArrayList();
    private final ObjectProperty<ActionResult> result = new SimpleObjectProperty<>();
    private final Instant createdAt = Instant.now();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public ManagedTask(String id, String name, ActionHandler handler, Map<String, Object> params) {
        this.id = id;
        this.name = name;
        this.handler = handler;
        this.params = params;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public ObjectProperty<Status> statusProperty() { return status; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty progressMessageProperty() { return progressMessage; }
    public ObservableList<String> getLogs() { return logs; }
    public ObjectProperty<ActionResult> resultProperty() { return result; }

    public void execute() {
        Platform.runLater(() -> status.set(Status.RUNNING));
        try {
            var actionResult = handler.execute(this, params);
            Platform.runLater(() -> {
                result.set(actionResult);
                status.set(switch (actionResult) {
                    case ActionResult.Success _ -> Status.COMPLETED;
                    case ActionResult.Failure _ -> Status.FAILED;
                    case ActionResult.Cancelled _ -> Status.CANCELLED;
                });
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                result.set(new ActionResult.Failure(e.getMessage(), e));
                status.set(Status.FAILED);
            });
        }
    }

    public void cancel() {
        cancelled.set(true);
        Platform.runLater(() -> status.set(Status.CANCELLED));
    }

    @Override
    public void updateProgress(double prog, String message) {
        Platform.runLater(() -> {
            progress.set(prog);
            progressMessage.set(message);
        });
    }

    @Override
    public void log(String message) {
        Platform.runLater(() -> logs.add(message));
    }

    @Override
    public boolean isCancelled() { return cancelled.get(); }
}
