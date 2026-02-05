package org.iumotionlabs.hefesto.desktop.observability;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class NotificationView extends VBox {

    private static final int MAX_VISIBLE = 3;

    public NotificationView() {
        setAlignment(Pos.TOP_RIGHT);
        setSpacing(8);
        setPadding(new Insets(10));
        setPickOnBounds(false);
        setMouseTransparent(true);

        NotificationService.getInstance().getNotifications().addListener(
            (javafx.collections.ListChangeListener<NotificationService.Notification>) change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        for (var notification : change.getAddedSubList()) {
                            showToast(notification);
                        }
                    }
                }
            }
        );
    }

    private void showToast(NotificationService.Notification notification) {
        var toast = new Label(notification.message());
        toast.getStyleClass().addAll("notification-toast", "notification-" + notification.level().name().toLowerCase());
        toast.setMaxWidth(300);
        toast.setWrapText(true);

        if (getChildren().size() >= MAX_VISIBLE) {
            getChildren().removeFirst();
        }
        getChildren().add(toast);

        var fadeIn = new FadeTransition(Duration.millis(200), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        var pause = new PauseTransition(Duration.seconds(4));
        pause.setOnFinished(_ -> {
            var fadeOut = new FadeTransition(Duration.millis(300), toast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(_ -> getChildren().remove(toast));
            fadeOut.play();
        });
        pause.play();
    }
}
