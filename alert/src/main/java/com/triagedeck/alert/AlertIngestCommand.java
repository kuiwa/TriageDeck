package com.triagedeck.alert.domain;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record AlertIngestCommand(
        String source,
        String type,
        String resourceId,
        String eventCode,
        Severity severity,
        String message,
        Instant occurredAt,
        JsonNode payload) {
}
