package com.msa.auth.kafka.producer;

import com.msa.common.kafka_event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String USER_CREATED_TOPIC = "user-created";

    public void publishUserCreated(UserCreatedEvent event) {

        // tenantKey를 Kafka key로 사용
        kafkaTemplate.send(
                USER_CREATED_TOPIC,
                event.getTenantKey(),
                event
        ).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error(
                        "Failed to publish UserCreatedEvent. tenantKey={}",
                        event.getTenantKey(),
                        ex
                );
            } else {
                log.info(
                        "UserCreatedEvent published. topic={}, partition={}, offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );
            }
        });
    }
}
