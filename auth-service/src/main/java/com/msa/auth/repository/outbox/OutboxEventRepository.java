package com.msa.auth.repository.outbox;

import com.msa.auth.entity.outbox.OutboxEvent;
import com.msa.auth.entity.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * PENDING인 항목 조회
     * 처음 발행하는 이벤트이거나, 재시도 예정 시간이 이미 지난 이벤트 100건을 조회
     * 해당 조건이 없으면 미래에 재시도 계획으로 업데이트 된 항목조차 조회한다.
     * 본인은 재시도 간격/백오프를 사용하므로 해당 조건이 필요
     *
     * FOR UPDATE SKIP LOCKED 사용이유
     * FOR UPDATE:
     * - 조회한 행에 배타적 row lock을 획득
     * - Service는 같은 트랜잭션에서 조회된 행을 PROCESSING으로 변경
     * - 이를 통해 여러 Publisher가 동일한 이벤트를 동시에 선점하는 것을 방지
     * - 비관적 락
     *
     * SKIP LOCKED:
     * - 다른 Publisher가 이미 잠근 행의 lock 해제를 기다리지 않고 건너 뜀
     * - 각 Publisher가 서로 다른 이벤트를 선점하여 병렬로 처리할 수 있음
     * - 락 점유로 인한 쓰레드 블로킹과 레이턴시가 상대적으로 적어진다.
     *
     * 트랜잭션 커밋 시 row lock 해제(반드시 쓰기 트랜잭션 안에서 호출해야 함)
     */
    @Query(
            value = """
                    SELECT *
                    FROM outbox_event
                    WHERE status = 'PENDING'
                      AND (
                          next_retry_at IS NULL
                          OR next_retry_at <= :now
                      )
                    ORDER BY created_at ASC
                    LIMIT 100
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxEvent> findPublishableEvents(@Param("now") LocalDateTime now);

    // 조건부 업데이트로 원자성 보장: 실패 메시지 복구-FAILED -> PENDING
    @Modifying
    @Query("""
        UPDATE OutboxEvent e
           SET e.status = :pendingStatus,
               e.retryCount = 0,
               e.nextRetryAt = null,
               e.processingStartedAt = null,
               e.lastError = null
         WHERE e.eventId = :eventId
           AND e.status = :failedStatus
        """)
    int retryFailedEvent(
            @Param("eventId") String eventId,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("failedStatus") OutboxStatus failedStatus
    );

    // 조건부 업데이트로 원자성 보장: 메시지 발행 성공-PROCESSING -> PUBLISHED
    @Modifying
    @Query("""
        UPDATE OutboxEvent e
           SET e.status = :publishedStatus,
               e.publishedAt = :publishedAt,
               e.nextRetryAt = null,
               e.lastError = null
         WHERE e.eventId = :eventId
           AND e.status = :processingStatus
        """)
    int markPublished(
            @Param("eventId") String eventId,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("publishedStatus") OutboxStatus publishedStatus,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    // 조건부 업데이트로 원자성 보장: 재시도 가능한 경우-PROCESSING -> PENDING
    @Modifying
    @Query("""
        UPDATE OutboxEvent e
           SET e.status = :pendingStatus,
               e.retryCount = e.retryCount + 1,
               e.nextRetryAt = :nextRetryAt,
               e.lastError = :errorMessage
         WHERE e.eventId = :eventId
           AND e.status = :processingStatus
        """)
    int markPendingForRetry(
            @Param("eventId") String eventId,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("errorMessage") String errorMessage
    );

    // 조건부 업데이트로 원자성 보장: 최대 재시도 횟수 도달-PROCESSING -> FAILED
    @Modifying
    @Query("""
        UPDATE OutboxEvent e
           SET e.status = :failedStatus,
               e.retryCount = e.retryCount + 1,
               e.nextRetryAt = null,
               e.lastError = :errorMessage
         WHERE e.eventId = :eventId
           AND e.status = :processingStatus
        """)
    int markProcessingAsFailed(
            @Param("eventId") String eventId,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("failedStatus") OutboxStatus failedStatus,
            @Param("errorMessage") String errorMessage
    );
}
