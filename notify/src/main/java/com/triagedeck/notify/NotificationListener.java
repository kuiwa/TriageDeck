package com.triagedeck.notify.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** `alert.cleared` 入口；topic/group 来自配置，不硬编码。 */
@Component
public class NotificationListener {

    private final NotificationConsumerService consumerService;

    public NotificationListener(NotificationConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.cleared}",
            groupId = "${app.kafka.consumer.group.notify}")
    public void onMessage(ConsumerRecord<String, String> record) {
        consumerService.handle(record.topic(), record.partition(), record.offset(), record.value());
    }
}
