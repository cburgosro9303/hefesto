package org.iumotionlabs.hefesto.desktop.audit;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;

public final class AuditTrail {

    private static final AuditTrail INSTANCE = new AuditTrail();
    private static final int MAX_ENTRIES = 1000;

    private final ObservableList<AuditEntry> entries = FXCollections.observableArrayList();

    private AuditTrail() {}

    public static AuditTrail getInstance() { return INSTANCE; }

    public void record(String actionId, String user, String details, String result) {
        var entry = new AuditEntry(Instant.now(), actionId, user, details, result);
        entries.addFirst(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public ObservableList<AuditEntry> entries() {
        return FXCollections.unmodifiableObservableList(entries);
    }

    public void clear() {
        entries.clear();
    }
}
