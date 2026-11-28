package com.triagedeck.notify.kafka;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.triagedeck.platform.event.AlertClearedMessage;
import com.triagedeck.platform.kafka.ProcessedEventStore;

/**
 * `alert.cleared` 消费：幂等标记 + `notification` 落库在同一事务（lld/05 §4.2）。
 * 解析失败抛异常交给 DefaultErrorHandler → 重试 → DLT，不吞异常。
 */
@Service
public class NotificationConsumerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumerService.class);

    private static final String INSERT_SQL = """
            insert into notification (id, alert_id, channel, payload, created_at)
            values (:id, :alertId, 'LOG', cast(:payload as jsonb), now())
            """;

    private final ProcessedEventStore processedEventStore;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final String consumerGroup;

    public NotificationConsumerService(
            ProcessedEventStore processedEventStore,
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${app.kafka.consumer.group.notify}") String consumerGroup) {
        this.processedEventStore = processedEventStore;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.consumerGroup = consumerGroup;
    }

    @Transactional
    public void handle(String topic, int partition, long offset, String payload) {
        if (!processedEventStore.tryMark(consumerGroup, topic, partition, offset)) {
            log.info("action=notify_skipped topic={} partition={} offset={}",
                    topic, partition, offset);
            return;
        }

        AlertClearedMessage message = parse(payload);
        jdbc.update(INSERT_SQL, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("alertId", message.alertId())
                .addValue("payload", payload));
        log.info("action=notify_cleared alertId={} topic={} offset={}",
                message.alertId(), topic, offset);
    }

    AlertClearedMessage parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("alert.cleared 消息体为空");
        }
        try {
            AlertClearedMessage message = objectMapper.readValue(payload, AlertClearedMessage.class);
            if (message == null || message.alertId() == null) {
                throw new IllegalArgumentException("alert.cleared 缺少 alertId");
            }
            return message;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("alert.cleared 消息体非法", exception);
        }
    }
}
