package com.triagedeck.alert.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * `alert.ingested` 入口（第二摄入通道，与 HTTP #5 走同一个 service）。
 * topic / 消费组全部来自配置，不硬编码 topic 名。
 */
@Component
public class AlertIngestListener {

    private final AlertKafkaConsumerService consumerService;

    public AlertIngestListener(AlertKafkaConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.ingested}",
            groupId = "${app.kafka.consumer.group.ingest}")
    public void onMessage(ConsumerRecord<String, String> record) {
        consumerService.handle(record.topic(), record.partition(), record.offset(), record.value());
    }
}
