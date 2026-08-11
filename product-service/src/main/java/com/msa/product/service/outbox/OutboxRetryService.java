package com.msa.product.service.outbox;

import com.msa.product.entity.outbox.OutboxStatus;
import com.msa.product.repository.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxRetryService {
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void retry(String eventId){
        // 관리자 A: FAILED 조회
        // 관리자 B: FAILED 조회
        // A PENDING 변경 및 커밋 -> Publisher: PENDING 선점 -> PROCESSING 변경
        // B 이전에 조회한 FAILED 엔티티를 PENDING으로 변경 -> 값이 다시 PENDING으로 덮어써짐
        // retry()의 FAILED -> PENDING 변경 자체가 원자적이여야함
        // 따라서, 조건부 업데이트 사용
        int updatedCount = outboxEventRepository.retryFailedEvent(eventId, OutboxStatus.PENDING, OutboxStatus.FAILED);

        if (updatedCount == 0) {
            throw new IllegalStateException(
                    "Outbox Event is not FAILED or is already being retried: " + eventId
            );
        }
    }
}
