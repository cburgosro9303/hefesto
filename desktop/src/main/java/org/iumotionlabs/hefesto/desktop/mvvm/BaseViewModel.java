package org.iumotionlabs.hefesto.desktop.mvvm;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseViewModel {

    private final List<Runnable> disposables = new ArrayList<>();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty();

    protected final UiScheduler scheduler;

    protected BaseViewModel() {
        this(Platform::runLater);
    }

    protected BaseViewModel(UiScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public BooleanProperty busyProperty() { return busy; }
    public boolean isBusy() { return busy.get(); }
    protected void setBusy(boolean value) { scheduler.runOnUiThread(() -> busy.set(value)); }

    public StringProperty errorMessageProperty() { return errorMessage; }
    public String getErrorMessage() { return errorMessage.get(); }
    protected void setError(String message) { scheduler.runOnUiThread(() -> errorMessage.set(message)); }
    protected void clearError() { setError(null); }

    protected void addDisposable(Runnable cleanup) {
        disposables.add(cleanup);
    }

    public void dispose() {
        disposables.forEach(Runnable::run);
        disposables.clear();
    }
}
