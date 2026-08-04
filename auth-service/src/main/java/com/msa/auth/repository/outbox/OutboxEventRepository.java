package com.msa.auth.repository.outbox;

import com.msa.auth.entity.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
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
                    """,
            nativeQuery = true
    )
    List<OutboxEvent> findPublishableEvents(@Param("now") LocalDateTime now);
}
