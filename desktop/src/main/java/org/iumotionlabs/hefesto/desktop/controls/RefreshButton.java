package org.iumotionlabs.hefesto.desktop.controls;

import javafx.animation.*;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

public class RefreshButton extends Button {

    private final BooleanProperty autoRefresh = new SimpleBooleanProperty(false);
    private final RotateTransition rotateAnimation;
    private Timeline autoRefreshTimeline;

    public RefreshButton() {
        setText("\u21BB"); // ↻
        getStyleClass().add("refresh-button");
        setTooltip(new Tooltip("Refresh"));

        rotateAnimation = new RotateTransition(Duration.millis(500), this);
        rotateAnimation.setByAngle(360);
        rotateAnimation.setAxis(Rotate.Z_AXIS);
    }

    public BooleanProperty autoRefreshProperty() { return autoRefresh; }

    public void playAnimation() {
        rotateAnimation.playFromStart();
    }

    public void startAutoRefresh(Duration interval, Runnable action) {
        stopAutoRefresh();
        autoRefreshTimeline = new Timeline(new KeyFrame(interval, _ -> {
            playAnimation();
            action.run();
        }));
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        autoRefreshTimeline.play();
        autoRefresh.set(true);
    }

    public void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
            autoRefreshTimeline = null;
        }
        autoRefresh.set(false);
    }
}
