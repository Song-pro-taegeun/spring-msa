package com.msa.auth.kafka.producer;

import com.msa.common.kafka_event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

// 카프카 발행순서 3
// Kafka 실제 전송 영역
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String USER_CREATED_TOPIC = "user-created";

    // 유저 생성 토픽 발행 -> 각 서비스에서 해당 토픽을 소비하여 테넌트 생성 및 flyway 실행
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
