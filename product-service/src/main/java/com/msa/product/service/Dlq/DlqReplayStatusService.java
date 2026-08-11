package com.msa.product.service.Dlq;

import com.msa.product.entity.dlq.DlqMessageEntity;
import com.msa.product.repository.dlq.DlqMessageRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DlqReplayStatusService {
    private final DlqMessageRepository dlqMessageRepository;

    /**
     * DLQ 재처리 중
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReplaying(Long dlqInfoId) {
        // 별도 트랜잭션의 영속성 컨텍스트에서 상태를 변경하기 위해 다시 조회
        DlqMessageEntity entity = getEntity(dlqInfoId);
        entity.markReplaying();
    }

    /**
     * DLQ 재처리 완료
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReplayed(Long dlqInfoId) {
        // 별도 트랜잭션의 영속성 컨텍스트에서 상태를 변경하기 위해 다시 조회
        DlqMessageEntity entity = getEntity(dlqInfoId);
        entity.markReplayed();
    }

    /**
     * DLQ 재처리 실패
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long dlqInfoId) {
        // 별도 트랜잭션의 영속성 컨텍스트에서 상태를 변경하기 위해 다시 조회
        DlqMessageEntity entity = getEntity(dlqInfoId);
        entity.markFailed();
    }

    /**
     * DLQ 재처리 무시
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIgnored(Long dlqInfoId) {
        // 별도 트랜잭션의 영속성 컨텍스트에서 상태를 변경하기 위해 다시 조회
        DlqMessageEntity entity = getEntity(dlqInfoId);
        entity.markIgnored();
    }

    private DlqMessageEntity getEntity(Long dlqInfoId) {
        return dlqMessageRepository.findById(dlqInfoId)
                .orElseThrow(() -> new ResourceNotFoundException("DLQ not found. id=" + dlqInfoId));
    }
}
