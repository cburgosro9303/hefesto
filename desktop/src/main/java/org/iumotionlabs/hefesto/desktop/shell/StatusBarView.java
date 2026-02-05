package org.iumotionlabs.hefesto.desktop.shell;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class StatusBarView extends HBox {

    private final Label statusLabel = new Label("Ready");
    private final Label taskCountLabel = new Label();
    private final Label clockLabel = new Label();

    public StatusBarView() {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(4, 10, 4, 10));

        statusLabel.getStyleClass().add("status-text");
        taskCountLabel.getStyleClass().add("status-task-count");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        clockLabel.getStyleClass().add("status-clock");
        updateClock();

        var clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), _ -> updateClock()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();

        getChildren().addAll(statusLabel, taskCountLabel, spacer, clockLabel);
    }

    public void setStatusText(String text) { statusLabel.setText(text); }

    public void setTaskCount(int count) {
        taskCountLabel.setText(count > 0 ? count + " task(s) running" : "");
    }

    private void updateClock() {
        clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }
}
