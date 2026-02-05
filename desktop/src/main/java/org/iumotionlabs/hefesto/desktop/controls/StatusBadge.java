package org.iumotionlabs.hefesto.desktop.controls;

import javafx.scene.control.Label;

public class StatusBadge extends Label {

    public enum Severity {
        CRITICAL("status-badge-critical"),
        HIGH("status-badge-high"),
        WARNING("status-badge-warning"),
        INFO("status-badge-info"),
        SUCCESS("status-badge-success");

        private final String styleClass;
        Severity(String styleClass) { this.styleClass = styleClass; }
        public String styleClass() { return styleClass; }
    }

    public StatusBadge() {
        getStyleClass().add("status-badge");
    }

    public StatusBadge(String text, Severity severity) {
        this();
        setText(text);
        setSeverity(severity);
    }

    public void setSeverity(Severity severity) {
        getStyleClass().removeIf(s -> s.startsWith("status-badge-"));
        getStyleClass().add(severity.styleClass());
    }
}
