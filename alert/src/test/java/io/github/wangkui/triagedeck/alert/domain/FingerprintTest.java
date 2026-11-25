package io.github.wangkui.triagedeck.alert.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import org.junit.jupiter.api.Test;

class FingerprintTest {

    @Test
    void should_ignore_case_and_outer_whitespace() {
        Fingerprint first = Fingerprint.of(command(" Payment-API ", " 5XX ", " Pay-01 ", " RULE "));
        Fingerprint second = Fingerprint.of(command("payment-api", "5xx", "pay-01", "rule"));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void should_treat_null_event_code_as_empty() {
        assertThat(Fingerprint.of(command("source", "type", "resource", null)))
                .isEqualTo(Fingerprint.of(command("source", "type", "resource", "")));
    }

    @Test
    void should_preserve_segment_order() {
        assertThat(Fingerprint.of(command("source-a", "type-b", "resource", "event")))
                .isNotEqualTo(Fingerprint.of(command("type-b", "source-a", "resource", "event")));
    }

    @Test
    void should_return_sha256_hex_for_fixed_segments() {
        assertThat(Fingerprint.of(command("a", "b", "c", "d")).value())
                .isEqualTo("1a84020153d6ff9728d1655a1f92be92aa83285aef65d5a7370772f9b279ba10");
    }

    @Test
    void should_ignore_message_time_severity_and_payload() {
        AlertIngestCommand first = command("source", "type", "resource", "event");
        AlertIngestCommand second = new AlertIngestCommand(
                "source",
                "type",
                "resource",
                "event",
                Severity.CRITICAL,
                "message with different number 42",
                Instant.parse("2026-11-25T06:05:00Z"),
                JsonNodeFactory.instance.objectNode().put("count", 42));

        assertThat(Fingerprint.of(second)).isEqualTo(Fingerprint.of(first));
    }

    private static AlertIngestCommand command(
            String source,
            String type,
            String resourceId,
            String eventCode) {
        return new AlertIngestCommand(
                source,
                type,
                resourceId,
                eventCode,
                Severity.MEDIUM,
                "message",
                Instant.parse("2026-11-25T06:00:00Z"),
                null);
    }
}
