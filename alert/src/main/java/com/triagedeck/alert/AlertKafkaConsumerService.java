package com.triagedeck.alert.kafka;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.triagedeck.alert.domain.AlertIngestCommand;
import com.triagedeck.alert.domain.AlertIngestResult;
import com.triagedeck.alert.domain.AlertIngestValidationException;
import com.triagedeck.alert.domain.Ingress;
import com.triagedeck.alert.domain.Severity;
import com.triagedeck.alert.service.AlertIngestService;
import com.triagedeck.platform.event.AlertIngestedMessage;
import com.triagedeck.platform.kafka.ProcessedEventStore;

/**
 * Kafka 摄入 service：幂等标记 + 摄入在**同一个事务**里（lld/05 §4.1）。
 * 解析/校验失败一律抛异常，交给 DefaultErrorHandler 重试后进 DLT，绝不吞异常返回正常。
 */
@Service
public class AlertKafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(AlertKafkaConsumerService.class);

    private final ProcessedEventStore processedEventStore;
    private final AlertIngestService ingestService;
    private final ObjectMapper objectMapper;
    private final String consumerGroup;

    public AlertKafkaConsumerService(
            ProcessedEventStore processedEventStore,
            AlertIngestService ingestService,
            ObjectMapper objectMapper,
            @Value("${app.kafka.consumer.group.ingest}") String consumerGroup) {
        this.processedEventStore = processedEventStore;
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
        this.consumerGroup = consumerGroup;
    }

    @Transactional
    public UUID handle(String topic, int partition, long offset, String payload) {
        if (!processedEventStore.tryMark(consumerGroup, topic, partition, offset)) {
            log.info("action=kafka_ingest_skipped topic={} partition={} offset={}",
                    topic, partition, offset);
            return null;
        }

        AlertIngestResult result = ingestService.ingest(toCommand(parse(payload)), Ingress.KAFKA);
        log.info("action=kafka_ingest alertId={} created={} topic={} offset={}",
                result.id(), result.created(), topic, offset);
        return result.id();
    }

    AlertIngestedMessage parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new AlertIngestValidationException();
        }
        try {
            AlertIngestedMessage message = objectMapper.readValue(payload, AlertIngestedMessage.class);
            if (message == null) {
                throw new AlertIngestValidationException();
            }
            return message;
        } catch (JsonProcessingException exception) {
            throw new AlertIngestValidationException();
        }
    }

    AlertIngestCommand toCommand(AlertIngestedMessage message) {
        return new AlertIngestCommand(
                message.source(),
                message.type(),
                message.resourceId(),
                message.eventCode(),
                severityOf(message.severity()),
                message.message(),
                message.occurredAt(),
                message.payload());
    }

    private static Severity severityOf(String value) {
        if (value == null || value.isBlank()) {
            throw new AlertIngestValidationException();
        }
        try {
            return Severity.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AlertIngestValidationException();
        }
    }
}
