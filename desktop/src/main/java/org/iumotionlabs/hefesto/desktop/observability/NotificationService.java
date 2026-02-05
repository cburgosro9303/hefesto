package org.iumotionlabs.hefesto.desktop.observability;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;

public final class NotificationService {

    public enum Level { INFO, WARNING, ERROR, SUCCESS }

    public record Notification(String message, Level level, Instant timestamp) {}

    private static final NotificationService INSTANCE = new NotificationService();
    private final ObservableList<Notification> notifications = FXCollections.observableArrayList();

    private NotificationService() {}

    public static NotificationService getInstance() { return INSTANCE; }

    public ObservableList<Notification> getNotifications() { return notifications; }

    public void info(String message) { add(message, Level.INFO); }
    public void warning(String message) { add(message, Level.WARNING); }
    public void error(String message) { add(message, Level.ERROR); }
    public void success(String message) { add(message, Level.SUCCESS); }

    private void add(String message, Level level) {
        notifications.add(new Notification(message, level, Instant.now()));
        if (notifications.size() > 50) {
            notifications.removeFirst();
        }
    }
}
