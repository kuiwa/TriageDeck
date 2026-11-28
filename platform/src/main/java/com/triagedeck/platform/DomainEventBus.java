package com.triagedeck.platform.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 领域事件出口。写库成功的消息**只能**在事务提交后发（02-design §5.3）：
 * `publishAfterCommit` 用 {@link TransactionSynchronizationManager} 注册回调，无事务时立即发送。
 */
@Component
public class DomainEventBus {

    private static final Logger log = LoggerFactory.getLogger(DomainEventBus.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String ingestedTopic;
    private final String clearedTopic;

    public DomainEventBus(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.ingested}") String ingestedTopic,
            @Value("${app.kafka.topics.cleared}") String clearedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ingestedTopic = ingestedTopic;
        this.clearedTopic = clearedTopic;
    }

    /** 立即发送；仅用于没有业务事务的场景（消费者内部禁止使用，用 publishAfterCommit）。 */
    public void publish(Object event) {
        send(event);
    }

    /** 有事务时注册 afterCommit 回调；回滚则一条都不发。 */
    public void publishAfterCommit(Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
            return;
        }
        send(event);
    }

    private void send(Object event) {
        String topic = topicOf(event);
        String key = keyOf(event);
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, payload).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("action=domain_event_send topic={} key={} error={}",
                            topic, key, error.getClass().getSimpleName());
                }
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "领域事件序列化失败：" + event.getClass().getSimpleName(), exception);
        }
    }

    private String topicOf(Object event) {
        if (event instanceof AlertIngestedMessage) {
            return ingestedTopic;
        }
        if (event instanceof AlertClearedMessage) {
            return clearedTopic;
        }
        throw new IllegalArgumentException("未登记 topic 的事件类型：" + event.getClass().getName());
    }

    private String keyOf(Object event) {
        if (event instanceof AlertClearedMessage cleared) {
            return cleared.alertId().toString();
        }
        // alert.ingested 由外部设备投递，key 由生产方给 fingerprint；应用内不发这类消息
        return null;
    }
}
