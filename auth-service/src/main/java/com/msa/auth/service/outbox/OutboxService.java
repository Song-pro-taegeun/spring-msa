package com.msa.auth.service.outbox;

import com.msa.auth.entity.outbox.OutboxEvent;
import com.msa.auth.entity.outbox.OutboxStatus;
import com.msa.auth.repository.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxService {
    @Value("${spring.outbox.publisher.retry-count:5}")
    private int maxRetryCount;

    private final OutboxEventRepository outboxEventRepository;

    /**
     * 명시적 새로운 트랜잭션 경계 설정: 메시지 발행 대상 조회 후 PROCESSING으로 변경
     * - row lock으로 PENDING 이벤트의 동시 선점을 방지
     * - 현재 Publisher가 선점한 이벤트를 PROCESSING으로 변경.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claimPendingEvents() {
        // 비관적 락
        List<OutboxEvent> events = outboxEventRepository.findPublishableEvents(LocalDateTime.now());

        // 디버그 시 해당 로직에 break point를 걸기 위해 조건문 사용
        // List.forEach는 빈 배열이면 실행하지 않지만 위 이유로 작성
        if(!events.isEmpty()){
            events.forEach(OutboxEvent::markProcessing);
        }
        return events;
    }

    /**
     * 명시적 새로운 트랜잭션 경계 설정: 메시지 발행 성공
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(String eventId) {
        int updatedCount = outboxEventRepository.markPublished(
                eventId,
                OutboxStatus.PROCESSING,
                OutboxStatus.PUBLISHED,
                LocalDateTime.now()
        );

        if (updatedCount == 0) {
            throw new IllegalStateException(
                    "Outbox Event is not PROCESSING or does not exist: " + eventId
            );
        }
    }

    /**
     * 명시적 새로운 트랜잭션 경계 설정: 재시도 카운트에 따른 pending(Retry) / failed 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePublishFailure(
            String eventId,
            String errorMessage
    ) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Outbox 이벤트가 없습니다: " + eventId)
                );

        int nextRetryCount = event.getRetryCount() + 1;
        int updatedCount;

        // 재시도+1 카운트가 설정한 재시도 카운트와 같거나 초과했다면, 실패처리
        if (nextRetryCount >= maxRetryCount) {
            // markFailed()를 호출하지 않은 이유
            // - 같은 클래스 에서 트랜잭션이 이미 열렸는데 새로운 트랜잭션 경계를 연다고 명시된 메서드를 호출
            // - 근데 이미 프록시로 트랜잭션이 감싸져 있어서 새로운 경계를 연다는건 무시
            // - Repository 메서드를 직접 호출하는 편이 트랜잭션 의미가 더 명확
            updatedCount = outboxEventRepository.markProcessingAsFailed(
                    eventId,
                    OutboxStatus.PROCESSING,
                    OutboxStatus.FAILED,
                    errorMessage
        );
        } else {
            // 재시도가 가능한 카운트라면 재시도 진행
            LocalDateTime nextRetryAt = LocalDateTime.now()
                    .plusSeconds(calculateRetryDelaySeconds(nextRetryCount)); // 다음 재시도 시간을 지수 백오프 계산

            updatedCount = outboxEventRepository.markPendingForRetry(
                    eventId,
                    OutboxStatus.PROCESSING,
                    OutboxStatus.PENDING,
                    nextRetryAt,
                    errorMessage
            );
        }

        if (updatedCount == 0) {
            throw new IllegalStateException(
                    "Outbox Event 상태가 PROCESSING이 아닙니다: " + eventId
            );
        }
    }

    /**
     * 명시적 새로운 트랜잭션 경계 설정: 메시지 발행 실패
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            String eventId,
            String errorMessage
    ) {
        int updatedCount = outboxEventRepository.markProcessingAsFailed(
                eventId,
                OutboxStatus.PROCESSING,
                OutboxStatus.FAILED,
                errorMessage
        );

        if (updatedCount == 0) {
            throw new IllegalStateException(
                    "Outbox Event 상태가 PROCESSING이 아닙니다: " + eventId
            );
        }
    }

    /**
     * 시간을 2배씩 늘리는 지수 백오프 계산 사용
     * 서버 장애로 인해 모든 요청이 재시도 된다면 같은 시간에 몰림을 방지하기 위함으로 제곱 형태로 재시도를 진행.
     */
    private long calculateRetryDelaySeconds(int retryCount) {
        return Math.min(300L, 1L << retryCount);
    }
}
