package io.github.wangkui.triagedeck.alert.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.wangkui.triagedeck.alert.domain.AlertIngestCommand;
import io.github.wangkui.triagedeck.alert.domain.Severity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlertIngestRequest(
        @NotBlank String source,
        @NotBlank String type,
        @NotBlank String resourceId,
        String eventCode,
        @NotNull Severity severity,
        @NotBlank String message,
        @NotNull Instant occurredAt,
        JsonNode payload) {

    AlertIngestCommand toCommand() {
        return new AlertIngestCommand(
                source,
                type,
                resourceId,
                eventCode,
                severity,
                message,
                occurredAt,
                payload);
    }
}
