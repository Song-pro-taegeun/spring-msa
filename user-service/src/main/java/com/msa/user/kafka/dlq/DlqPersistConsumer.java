package com.msa.user.kafka.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.common.kafka_event.DlqMessage;
import com.msa.common.kafka_event.UserCreatedEvent;
import com.msa.tenant.config.TenantContext;
import com.msa.user.service.Dlq.DlqService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;


/**
 * DLQ 토픽 컨슈머(DLQ 공통)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqPersistConsumer {
    @Value("${spring.base-schema-name}")
    private String baseSchemaName;

    private final ObjectMapper objectMapper;
    private final DlqService dlqService;

    @KafkaListener(
            topics = "user-service.user-created.DLQ",
            groupId = "user-service-dlq-persistor",
            containerFactory = "dlqPersistKafkaListenerContainerFactory" // DLQ 오류는 또 다른 DLQ 토픽을 만들지 않기 위해 새로운 컨테이너 팩토리를 구성하여 해당 팩토리를 빈으로 사용
    )

    public void persist(
            ConsumerRecord<String, DlqMessage<?>> record,
            Acknowledgment ack
    ) {
        DlqMessage<?> dlq = record.value();
        String tenantKey = extractTenantKey(dlq); // 테넌트 스키마

        try {
            // 환경변수의 baseSchemaName과 테넌트 키의 조합으로 스키마를 선택
            TenantContext.set(baseSchemaName + "_" + tenantKey);

            // DB에 DLQ 정보 등록
            dlqService.persistDlq(dlq, record);

            // Offset 전진
            ack.acknowledge();
        } finally {
            TenantContext.clear();
        }
    }

    private String extractTenantKey(DlqMessage<?> dlq) {
        try {
            UserCreatedEvent event = objectMapper.convertValue(dlq.getPayload(), UserCreatedEvent.class);
            return event.getTenantKey();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Cannot extract tenantKey from DLQ payload", e);
        }
    }
}
