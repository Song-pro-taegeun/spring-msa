package com.msa.user.service.Dlq;

import com.msa.user.entity.dlq.DlqMessageEntity;
import com.msa.user.repository.dlq.DlqMessageRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DlqReplayStatusService {
    private final DlqMessageRepository dlqMessageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReplaying(Long dlqInfoId) {
        // 별도 트랜잭션의 영속성 컨텍스트에서 상태를 변경하기 위해 다시 조회
        DlqMessageEntity entity = getEntity(dlqInfoId);
        entity.markReplaying();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReplayed(Long dlqInfoId) {
        // 별도 트랜잭션의 영속성 컨텍스트에서 상태를 변경하기 위해 다시 조회
        DlqMessageEntity entity = getEntity(dlqInfoId);
        entity.markReplayed();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long dlqInfoId) {
        // 별도 트랜잭션의 영속성 컨텍스트에서 상태를 변경하기 위해 다시 조회
        DlqMessageEntity entity = getEntity(dlqInfoId);
        entity.markFailed();
    }

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
