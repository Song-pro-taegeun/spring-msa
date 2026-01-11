package com.msa.user.service.Dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.common.kafka_event.DlqMessage;
import com.msa.user.entity.dlq.DlqCreateBy;
import com.msa.user.entity.dlq.DlqMessageEntity;
import com.msa.user.entity.dlq.DlqStatus;
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
    public void persistDlq(DlqMessage<?> dlq, ConsumerRecord<String, DlqMessage<?>> record){
        try {
            DlqMessageEntity entity = new DlqMessageEntity();
            entity.setOriginalTopic(dlq.getOriginalTopic());
            entity.setOriginalPartition(dlq.getOriginalPartition());
            entity.setOriginalOffset(dlq.getOriginalOffset());
            entity.setKafkaMessageKey(record.key());
            entity.setExceptionClass(dlq.getExceptionClass());
            entity.setExceptionMessage(dlq.getExceptionMessage());
            entity.setStackTrace(dlq.getStackTrace());
            entity.setPayloadJson(objectMapper.writeValueAsString(dlq.getPayload()));
            entity.setStatus(DlqStatus.NEW);
            entity.setCreatedBy(DlqCreateBy.CONSUMER);
            dlqRepository.save(entity);
        } catch (JsonProcessingException e){
            throw new IllegalStateException("DLQ payload serialization failed", e);
        }
    }
}
