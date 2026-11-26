package com.triagedeck.alert.domain;

import java.time.Instant;
import java.util.UUID;

public record AlertIngestResult(
        UUID id,
        boolean created,
        int occurrenceCount,
        Severity severity,
        Instant lastSeenAt) {
}
