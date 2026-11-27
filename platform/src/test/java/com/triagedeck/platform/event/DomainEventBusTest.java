package com.triagedeck.platform.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class DomainEventBusTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final DomainEventBus bus = new DomainEventBus(
            kafkaTemplate,
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
            "alert.ingested",
            "alert.cleared");

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void should_not_send_before_commit() {
        when(kafkaTemplate.send(anyString(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        TransactionSynchronizationManager.initSynchronization();

        bus.publishAfterCommit(clearedMessage());

        verify(kafkaTemplate, never()).send(anyString(), any(), anyString());
    }

    @Test
    void should_send_alert_cleared_after_commit_with_alert_id_as_key() {
        when(kafkaTemplate.send(anyString(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        TransactionSynchronizationManager.initSynchronization();
        AlertClearedMessage event = clearedMessage();

        bus.publishAfterCommit(event);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(kafkaTemplate).send("alert.cleared", event.alertId().toString(),
                "{\"alertId\":\"" + event.alertId() + "\",\"fingerprint\":\"fp-1\",\"severity\":\"CRITICAL\","
                        + "\"source\":\"payment-api\",\"type\":\"5xx\",\"resourceId\":\"pay-01\","
                        + "\"occurrenceCount\":12,\"clearedAt\":\"2026-11-23T02:10:00Z\",\"clearedBy\":\"operator\"}");
    }

    @Test
    void should_not_publish_when_rollback() {
        when(kafkaTemplate.send(anyString(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        TransactionSynchronizationManager.initSynchronization();

        bus.publishAfterCommit(clearedMessage());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization ->
                        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(kafkaTemplate, never()).send(anyString(), any(), anyString());
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
    }

    @Test
    void should_send_immediately_without_transaction() {
        when(kafkaTemplate.send(anyString(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        bus.publishAfterCommit(clearedMessage());

        verify(kafkaTemplate).send(anyString(), any(), anyString());
    }

    private static AlertClearedMessage clearedMessage() {
        return new AlertClearedMessage(
                UUID.fromString("7b0d2a52-3f26-4c31-9b0f-5d7b0f6c9a11"),
                "fp-1",
                "CRITICAL",
                "payment-api",
                "5xx",
                "pay-01",
                12,
                Instant.parse("2026-11-23T02:10:00Z"),
                "operator");
    }
}
