package io.github.wangkui.triagedeck.alert.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.github.wangkui.triagedeck.alert.domain.AlertIngestResult;

public record AlertIngestReceipt(
        UUID id,
        boolean created,
        int occurrenceCount,
        String severity,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant lastSeenAt) {

    static AlertIngestReceipt from(AlertIngestResult result) {
        return new AlertIngestReceipt(
                result.id(),
                result.created(),
                result.occurrenceCount(),
                result.severity().name(),
                result.lastSeenAt());
    }
}
