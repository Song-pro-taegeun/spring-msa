package com.msa.order.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.common.dto.CommonRequestProvisionReplayDlqDto;
import com.msa.common.kafka_event.TenantProvisionedEvent;
import com.msa.order.entity.dlq.DlqMessageEntity;
import com.msa.order.entity.dlq.DlqStatus;
import com.msa.order.repository.dlq.DlqMessageRepository;
import com.msa.order.service.dlq.DlqReplayStatusService;
import com.msa.order.service.order.TenantSchemaService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AdminService {
    private final DlqMessageRepository dlqMessageRepository;

    private final TenantSchemaService tenantSchemaService;
    private final DlqReplayStatusService dlqReplayStatusService;

    private final ObjectMapper objectMapper;

    /**
     * provision()은 DDL, DCL, Flyway migration, 동적 DataSource 연결을 포함하므로
     * 하나의 애플리케이션 트랜잭션으로 원자성을 보장하기 어려움
     * 따라서 replay 처리 자체에는 트랜잭션을 걸지 않고,
     * DLQ 상태 변경은 별도 서비스에서 REQUIRES_NEW 트랜잭션으로 즉시 커밋
     * 이렇게 하면 provision 실패로 예외가 발생해도 FAILED 상태 기록은 롤백되지 않도록 처리
     */
    public String replayProvisionDlq(CommonRequestProvisionReplayDlqDto inData){
        String topic = inData.topic();
        Integer partition = inData.partition();
        Long offset = inData.offset();

        DlqMessageEntity dlqMessageEntity = dlqMessageRepository.findByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                topic,
                partition,
                offset
        ).orElseThrow(() -> new ResourceNotFoundException(
                "DLQ provision row not found. topic=%s, partition=%d, offset=%d"
                        .formatted(topic, partition, offset)
        ));

        // 재처리 중 단계 기록
        int updated = dlqReplayStatusService.markReplaying(dlqMessageEntity.getId());
        if (updated == 0) {
            return "DLQ 재처리 실패(이미 처리 중이거나 완료된 이벤트)";
        }
        try {
            // json 역직렬화
            TenantProvisionedEvent event = objectMapper.readValue(
                    dlqMessageEntity.getPayloadJson(),
                    TenantProvisionedEvent.class
            );
            tenantSchemaService.provision(event);

            // 재처리 완료 단계 기록
            dlqReplayStatusService.markReplayed(dlqMessageEntity.getId());

            // markReplayed 새로운 트랜잭션 경계에서 값을 바꾸기에 리턴 값은 고정된 상태 값을 반환
            return "DLQ 재처리 완료. 이벤트 상태 = " + DlqStatus.REPLAYED;
        } catch (Exception e) {
            // 재처리 실패 단계 기록
            dlqReplayStatusService.markFailed(dlqMessageEntity.getId());
            throw new IllegalStateException("DLQ replay failed", e);
        }
    }
}
