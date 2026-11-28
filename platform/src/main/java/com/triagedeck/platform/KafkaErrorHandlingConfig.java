package com.triagedeck.platform.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 消费失败处理（02-design §5.5）：重试 2 次（间隔 1s）后投 DLT。
 * DLT topic 名 = 原 topic + `app.kafka.dlt-suffix`，**不硬编码**。
 * Spring Boot 会把本 Bean 装配到自动配置的监听容器工厂上。
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public CommonErrorHandler kafkaCommonErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.dlt-suffix}") String dltSuffix) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) ->
                        new TopicPartition(record.topic() + dltSuffix, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}
