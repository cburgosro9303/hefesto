package org.iumotionlabs.hefesto.desktop.controls;

import javafx.scene.control.*;
import org.iumotionlabs.hefesto.desktop.ServiceLocator;
import org.iumotionlabs.hefesto.desktop.audit.AuditTrail;
import org.iumotionlabs.hefesto.desktop.concurrency.HefestoExecutors;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;
import org.iumotionlabs.hefesto.desktop.observability.NotificationService;
import org.iumotionlabs.hefesto.feature.portinfo.service.PortInfoService;

public final class ProcessKillDialog {

    private ProcessKillDialog() {}

    public static void show(long pid, String processName) {
        var i18n = I18nService.getInstance();

        var forceCheck = new CheckBox(i18n.t("procwatch.kill.force"));
        forceCheck.setSelected(false);

        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(i18n.t("procwatch.kill.process"));
        alert.setHeaderText(i18n.t("procwatch.kill.confirm", processName, String.valueOf(pid)));
        alert.getDialogPane().setContent(forceCheck);

        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                executeKill(pid, processName, forceCheck.isSelected());
            }
        });
    }

    private static void executeKill(long pid, String processName, boolean force) {
        var i18n = I18nService.getInstance();
        var notifications = NotificationService.getInstance();
        var audit = AuditTrail.getInstance();

        HefestoExecutors.runAsync(
            () -> ServiceLocator.get(PortInfoService.class).killProcess(pid, force),
            success -> {
                if (success) {
                    notifications.success(i18n.t("procwatch.kill.success", processName));
                    audit.record("kill.process", System.getProperty("user.name"),
                        "Killed process %s (PID: %d, force: %b)".formatted(processName, pid, force), "SUCCESS");
                } else {
                    notifications.error(i18n.t("procwatch.kill.failed", processName, "Process could not be terminated"));
                    audit.record("kill.process", System.getProperty("user.name"),
                        "Failed to kill process %s (PID: %d)".formatted(processName, pid), "FAILED");
                }
            },
            error -> {
                notifications.error(i18n.t("procwatch.kill.failed", processName, error.getMessage()));
                audit.record("kill.process", System.getProperty("user.name"),
                    "Error killing process %s (PID: %d): %s".formatted(processName, pid, error.getMessage()), "ERROR");
            }
        );
    }
}
