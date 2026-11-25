package io.github.wangkui.triagedeck.alert.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.wangkui.triagedeck.alert.repo.AlertIngestRepository;
import io.github.wangkui.triagedeck.alert.service.AlertIngestService;

import org.junit.jupiter.api.Test;

class AlertIngestServiceTest {

    private final AlertIngestService service =
            new AlertIngestService(mock(AlertIngestRepository.class));
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_reject_when_required_fields_are_blank() {
        assertThatThrownBy(() -> service.ingest(command("  ", "type", "resource", "event", null), Ingress.HTTP))
                .isInstanceOf(AlertIngestValidationException.class);
    }

    @Test
    void should_reject_when_occurred_at_is_outside_the_allowed_window() {
        assertThatThrownBy(() -> service.ingest(
                        command(
                                "source",
                                "type",
                                "resource",
                                "event",
                                null,
                                Instant.now().minusSeconds(31L * 24 * 60 * 60)),
                        Ingress.HTTP))
                .isInstanceOf(AlertIngestValidationException.class);
    }

    @Test
    void should_reject_non_object_payload() throws Exception {
        JsonNode payload = objectMapper.readTree("[1,2,3]");

        assertThatThrownBy(() -> service.ingest(
                        command("source", "type", "resource", "event", payload),
                        Ingress.HTTP))
                .isInstanceOf(AlertIngestValidationException.class);
    }

    @Test
    void should_reject_payload_larger_than_64_kib() {
        JsonNode payload = objectMapper.createObjectNode().put("blob", "x".repeat(65_536));

        assertThatThrownBy(() -> service.ingest(
                        command("source", "type", "resource", "event", payload),
                        Ingress.HTTP))
                .isInstanceOf(AlertIngestValidationException.class);
    }

    private static AlertIngestCommand command(
            String source,
            String type,
            String resourceId,
            String eventCode,
            JsonNode payload) {
        return command(source, type, resourceId, eventCode, payload, Instant.now());
    }

    private static AlertIngestCommand command(
            String source,
            String type,
            String resourceId,
            String eventCode,
            JsonNode payload,
            Instant occurredAt) {
        return new AlertIngestCommand(
                source,
                type,
                resourceId,
                eventCode,
                Severity.MEDIUM,
                "message",
                occurredAt,
                payload);
    }
}
