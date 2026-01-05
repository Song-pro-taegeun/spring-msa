package com.msa.user.kafka.dlq;

import com.msa.common.kafka_event.DlqMessage;
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
    private final DlqService dlqService;

    @KafkaListener(
            topicPattern = "user-service\\..*\\.DLQ", // 토픽 이름을 고정하지 않고 패턴에 부합되는 모든 토픽은 동일한 컨슈머 사용
            groupId = "user-service-dlq-persistor",
            containerFactory = "dlqPersistKafkaListenerContainerFactory" // DLQ 오류는 또 다른 DLQ 토픽을 만들지 않기 위해 새로운 컨테이너 팩토리를 구성하여 해당 팩토리를 빈으로 사용
    )
    public void persist(
            ConsumerRecord<String, DlqMessage<?>> record,
            Acknowledgment ack
    ) {
        DlqMessage<?> dlq = record.value();
        try {
            // 오류 발생 시 msa_user 스키마에 DLQ 메시지 적재
            TenantContext.set(baseSchemaName);

            // DB에 DLQ 정보 등록
            dlqService.persistDlq(dlq, record);
        } finally {
            /**
             * DB에 DLQ 저장 로직마저도 예외 및 오류가 발생한다면, 토픽을 추가로 발행하지 않고 추후 운영 알림으로 발행해야함.
             * 해당 문제는 크리티컬한 운영 이슈로 로직에서 별도로 처리하지 않는다.
             * DLQ Persist Consumer는 최종 소비자이기 떄문에 운영알림 후 Offset은 무조건 전진시킨다.
             */
            ack.acknowledge();
            TenantContext.clear();
        }
    }
}