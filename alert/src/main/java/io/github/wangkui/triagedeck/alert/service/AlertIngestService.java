package io.github.wangkui.triagedeck.alert.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.wangkui.triagedeck.alert.domain.AlertIngestCommand;
import io.github.wangkui.triagedeck.alert.domain.AlertIngestResult;
import io.github.wangkui.triagedeck.alert.domain.AlertIngestValidationException;
import io.github.wangkui.triagedeck.alert.domain.Fingerprint;
import io.github.wangkui.triagedeck.alert.domain.Ingress;
import io.github.wangkui.triagedeck.alert.repo.AlertIngestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertIngestService {

    private static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final AlertIngestRepository repository;

    public AlertIngestService(AlertIngestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AlertIngestResult ingest(AlertIngestCommand command, Ingress ingress) {
        AlertIngestCommand normalized = validateAndNormalize(command);
        JsonNode payload = normalized.payload();
        String payloadJson = payload == null ? "{}" : payload.toString();
        UUID alertId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        return repository.upsertAndRecord(
                normalized,
                Fingerprint.of(normalized),
                ingress,
                alertId,
                eventId,
                payloadJson);
    }

    private static AlertIngestCommand validateAndNormalize(AlertIngestCommand command) {
        if (command == null || command.severity() == null || command.occurredAt() == null) {
            throw new AlertIngestValidationException();
        }

        String source = normalizedRequired(command.source(), 128);
        String type = normalizedRequired(command.type(), 128);
        String resourceId = normalizedRequired(command.resourceId(), 128);
        String eventCode = normalizedOptional(command.eventCode(), 128);
        String message = normalizedRequired(command.message(), 4000);
        Instant now = Instant.now();
        if (command.occurredAt().isAfter(now.plus(MAX_FUTURE_SKEW))) {
            throw new AlertIngestValidationException();
        }

        JsonNode payload = command.payload();
        if (payload != null && !payload.isObject()) {
            throw new AlertIngestValidationException();
        }
        if (payload != null
                && payload.toString().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new AlertIngestValidationException();
        }

        return new AlertIngestCommand(
                source,
                type,
                resourceId,
                eventCode,
                command.severity(),
                message,
                command.occurredAt(),
                payload);
    }

    private static String normalizedRequired(String value, int maxLength) {
        String normalized = value == null ? "" : value.strip();
        if (characterCount(normalized) < 1 || characterCount(normalized) > maxLength) {
            throw new AlertIngestValidationException();
        }
        return normalized;
    }

    private static String normalizedOptional(String value, int maxLength) {
        String normalized = value == null ? "" : value.strip();
        if (characterCount(normalized) > maxLength) {
            throw new AlertIngestValidationException();
        }
        return normalized;
    }

    private static int characterCount(String value) {
        return value.codePointCount(0, value.length());
    }
}
