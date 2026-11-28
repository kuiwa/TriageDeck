package com.triagedeck.platform.event;

import java.time.Instant;
import java.util.UUID;

/** `alert.cleared` 消息体（字段严格按 02b-contract.md §6.3）。 */
public record AlertClearedMessage(
        UUID alertId,
        String fingerprint,
        String severity,
        String source,
        String type,
        String resourceId,
        int occurrenceCount,
        Instant clearedAt,
        String clearedBy) {
}
