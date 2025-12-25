package com.msa.user.kafka.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.common.kafka_event.DlqMessage;
import com.msa.common.kafka_event.UserCreatedEvent;
import com.msa.tenant.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * kafka.dlq.replay.enabled=true일 때만 spring 컨텍스트에 등록
 * 현재는 false로 지정 -> 원본 consumer에도 dlq 발행 메시지의 경우 즉시 종료하도록 조치하였지만,
 * 환경변수에서 kafka.dlq.replay.enabled가 true일 때만 컨텍스트에 등록 되도록 조치
 *      -> 해당 로직과 원본 안전장치가 없으면, 토픽 구독 -> 익셉션 -> DLQ -> DLQ send -> 토픽 구독 -> 익셉션... 무한 루프가 동작 됨
 */
@ConditionalOnProperty(
        name = "kafka.dlq.replay.enabled", // 환경 설정 키 확인
        havingValue = "true"               // 해당 키 값이 true일 때, 컨텍스트 등록
)
@Slf4j
@RequiredArgsConstructor
@Component
public class UserDlqReplayConsumer {
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "user-service.user-created.DLQ",
            groupId = "user-service-dlq-replayer"
    )
    public void replay(
            DlqMessage<UserCreatedEvent> dlq,
            Acknowledgment ack
    ) {
        try {
            // 0. LinkedHashMap -> UserCreatedEvent로 변환(Payload가 T type임)
            Object payloadObj = dlq.getPayload();
            UserCreatedEvent event = objectMapper.convertValue(payloadObj, UserCreatedEvent.class);

            // 1. tenant 복구
            TenantContext.set(event.getTenantKey());

            log.warn("""
                DLQ Replay 시작
                originalTopic={}
                originalOffset={}
                exception={}
                """,
                    dlq.getOriginalTopic(),
                    dlq.getOriginalOffset(),
                    dlq.getExceptionClass()
            );

            // 2. 원본 토픽으로 재발행
            ProducerRecord<Object, Object> record =
                    new ProducerRecord<>(
                            dlq.getOriginalTopic(),
                            event.getTenantKey(),
                            event
                    );

            // 3. 헤더 추가 (무한 루프 방지용)
            record.headers().add("dlq-replayed", "true".getBytes());
            record.headers().add("dlq-original-offset",
                    String.valueOf(dlq.getOriginalOffset()).getBytes());

            kafkaTemplate.send(record);

            // 4. DLQ offset 커밋
            ack.acknowledge();
            log.info("DLQ Replay 성공 → {}", dlq.getOriginalTopic());

        } catch (Exception e) {
            log.error("DLQ Replay 실패", e);
        } finally {
            TenantContext.clear();
        }
    }
}
