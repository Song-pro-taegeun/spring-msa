package com.msa.user.config.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;


/**
 * DLQ 발행 방법을 제공
 * DLQ로 보낼 때 어느 토픽, 파티션으로 보낼지를 결정
 */
@Configuration
public class KafkaDlqConfig {
    @Value("${spring.application.name}")
    private String serviceName;

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) ->
                        new TopicPartition(serviceName + "." + record.topic() + ".DLQ", record.partition())
        );
    }
}

