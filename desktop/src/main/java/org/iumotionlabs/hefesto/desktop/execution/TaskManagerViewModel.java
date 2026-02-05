package org.iumotionlabs.hefesto.desktop.execution;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.iumotionlabs.hefesto.desktop.api.action.ActionHandler;
import org.iumotionlabs.hefesto.desktop.concurrency.HefestoExecutors;
import org.iumotionlabs.hefesto.desktop.mvvm.BaseViewModel;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskManagerViewModel extends BaseViewModel {

    private static final TaskManagerViewModel INSTANCE = new TaskManagerViewModel();
    private final ObservableList<ManagedTask> tasks = FXCollections.observableArrayList();
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    private TaskManagerViewModel() {}

    public static TaskManagerViewModel getInstance() { return INSTANCE; }

    public ObservableList<ManagedTask> getTasks() { return tasks; }

    public ManagedTask submit(String name, ActionHandler handler, Map<String, Object> params) {
        var task = new ManagedTask("task-" + taskCounter.incrementAndGet(), name, handler, params);
        tasks.add(task);
        HefestoExecutors.runOnIoThread(task::execute);
        return task;
    }

    public void cancelTask(String taskId) {
        tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .ifPresent(ManagedTask::cancel);
    }

    public long runningCount() {
        return tasks.stream().filter(t -> t.statusProperty().get() == ManagedTask.Status.RUNNING).count();
    }
}
