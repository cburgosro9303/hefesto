package org.iumotionlabs.hefesto.desktop.audit;

import java.time.Instant;

public record AuditEntry(
    Instant timestamp,
    String actionId,
    String user,
    String details,
    String result
) {}
