package com.triagedeck.alert.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.triagedeck.alert.domain.AlertIngestCommand;
import com.triagedeck.alert.domain.AlertIngestResult;
import com.triagedeck.alert.domain.AlertIngestValidationException;
import com.triagedeck.alert.domain.Ingress;
import com.triagedeck.alert.domain.Severity;
import com.triagedeck.alert.service.AlertIngestService;
import com.triagedeck.platform.kafka.ProcessedEventStore;

class AlertKafkaConsumerServiceTest {

    private final ProcessedEventStore processedEventStore = mock(ProcessedEventStore.class);
    private final AlertIngestService ingestService = mock(AlertIngestService.class);
    private final AlertKafkaConsumerService service = new AlertKafkaConsumerService(
            processedEventStore, ingestService,
            new ObjectMapper().registerModule(new JavaTimeModule()),
            "triagedeck-alert-ingest");

    @Test
    void should_skip_when_offset_already_processed() {
        when(processedEventStore.tryMark(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(false);

        assertThat(service.handle("alert.ingested", 0, 42L, validPayload())).isNull();

        verify(ingestService, never()).ingest(any(), any());
    }

    @Test
    void should_ingest_with_kafka_ingress_and_map_all_fields() {
        when(processedEventStore.tryMark("triagedeck-alert-ingest", "alert.ingested", 0, 7L))
                .thenReturn(true);
        UUID alertId = UUID.randomUUID();
        when(ingestService.ingest(any(AlertIngestCommand.class), any(Ingress.class)))
                .thenReturn(new AlertIngestResult(alertId, true, 1, Severity.CRITICAL, Instant.now()));

        assertThat(service.handle("alert.ingested", 0, 7L, validPayload())).isEqualTo(alertId);

        ArgumentCaptor<AlertIngestCommand> command = ArgumentCaptor.forClass(AlertIngestCommand.class);
        ArgumentCaptor<Ingress> ingress = ArgumentCaptor.forClass(Ingress.class);
        verify(ingestService).ingest(command.capture(), ingress.capture());
        assertThat(ingress.getValue()).isEqualTo(Ingress.KAFKA);
        assertThat(command.getValue().source()).isEqualTo("device-simulator");
        assertThat(command.getValue().type()).isEqualTo("threshold");
        assertThat(command.getValue().resourceId()).isEqualTo("device-1");
        assertThat(command.getValue().eventCode()).isEqualTo("CPU_HIGH");
        assertThat(command.getValue().severity()).isEqualTo(Severity.CRITICAL);
        assertThat(command.getValue().occurredAt()).isEqualTo(Instant.parse("2026-11-27T05:00:00Z"));
        assertThat(command.getValue().payload()).isNotNull();
    }

    @Test
    void should_reject_invalid_json_so_it_goes_to_dlt() {
        when(processedEventStore.tryMark(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.handle("alert.ingested", 0, 8L, "{\"broken\""))
                .isInstanceOf(AlertIngestValidationException.class);
        verify(ingestService, never()).ingest(any(), any());
    }

    @Test
    void should_reject_unknown_severity() {
        when(processedEventStore.tryMark(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.handle("alert.ingested", 0, 9L,
                validPayload().replace("\"CRITICAL\"", "\"BLOCKER\"")))
                .isInstanceOf(AlertIngestValidationException.class);
        verify(ingestService, never()).ingest(any(), any());
    }

    private static String validPayload() {
        return "{\"source\":\"device-simulator\",\"type\":\"threshold\",\"resourceId\":\"device-1\","
                + "\"eventCode\":\"CPU_HIGH\",\"severity\":\"CRITICAL\","
                + "\"message\":\"device-1 metric 92%\",\"occurredAt\":\"2026-11-27T05:00:00Z\","
                + "\"payload\":{\"metric\":92}}";
    }
}
