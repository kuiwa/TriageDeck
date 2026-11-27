package com.triagedeck.notify.kafka;

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

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.triagedeck.platform.kafka.ProcessedEventStore;

class NotificationConsumerServiceTest {

    private final ProcessedEventStore processedEventStore = mock(ProcessedEventStore.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final NotificationConsumerService service = new NotificationConsumerService(
            processedEventStore, jdbc,
            new ObjectMapper().registerModule(new JavaTimeModule()),
            "triagedeck-notify");

    @Test
    void should_skip_when_offset_already_processed() {
        when(processedEventStore.tryMark(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(false);

        service.handle("alert.cleared", 0, 3L, clearedPayload());

        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void should_write_notification_with_alert_id_from_message() {
        when(processedEventStore.tryMark("triagedeck-notify", "alert.cleared", 0, 4L))
                .thenReturn(true);
        String payload = clearedPayload();

        service.handle("alert.cleared", 0, 4L, payload);

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), params.capture());
        assertThat(params.getValue().getValue("alertId"))
                .isEqualTo(UUID.fromString("7b0d2a52-3f26-4c31-9b0f-5d7b0f6c9a11"));
        assertThat(params.getValue().getValue("payload")).isEqualTo(payload);
        assertThat(params.getValue().getValue("id")).isInstanceOf(UUID.class);
    }

    @Test
    void should_reject_invalid_payload_so_it_goes_to_dlt() {
        when(processedEventStore.tryMark(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.handle("alert.cleared", 0, 5L, "not-json"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    private static String clearedPayload() {
        return "{\"alertId\":\"7b0d2a52-3f26-4c31-9b0f-5d7b0f6c9a11\",\"fingerprint\":\"fp-1\","
                + "\"severity\":\"CRITICAL\",\"source\":\"payment-api\",\"type\":\"5xx\","
                + "\"resourceId\":\"pay-01\",\"occurrenceCount\":12,"
                + "\"clearedAt\":\"2026-11-23T02:10:00Z\",\"clearedBy\":\"operator\"}";
    }
}
