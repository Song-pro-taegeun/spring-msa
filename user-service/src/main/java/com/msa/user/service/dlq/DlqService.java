package com.msa.user.service.dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.common.kafka_event.DlqMessage;
import com.msa.user.entity.dlq.DlqMessageEntity;
import com.msa.user.repository.dlq.DlqMessageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;

/**
 * DLQ 발송 정보 DB 저장 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqService {
    private final DlqMessageRepository dlqRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void persistDlq(DlqMessage<?> dlq, ConsumerRecord<String, String> record) {
        // 원본 payload JSON 문자열을 DB 저장 형식으로 준비
        String payloadJson = serializePayload(dlq.getPayload());

        DlqMessageEntity entity = DlqMessageEntity.fromKafkaDlq(
                dlq,
                record.key(),
                payloadJson
        );

        dlqRepository.save(entity);
    }

    private String serializePayload(Object payload) {
        if (payload instanceof String payloadJson) {
            return payloadJson;
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DLQ payload serialization failed", e);
        }
    }
}
