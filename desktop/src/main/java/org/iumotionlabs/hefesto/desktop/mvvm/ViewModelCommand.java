package org.iumotionlabs.hefesto.desktop.mvvm;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class ViewModelCommand {

    private final Runnable action;
    private final BooleanProperty canExecute = new SimpleBooleanProperty(true);

    public ViewModelCommand(Runnable action) {
        this.action = action;
    }

    public BooleanProperty canExecuteProperty() { return canExecute; }

    public void setCanExecute(boolean value) { canExecute.set(value); }

    public void execute() {
        if (canExecute.get()) {
            action.run();
        }
    }
}
