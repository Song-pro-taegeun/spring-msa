package com.msa.auth.entity.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(
                        name = "idx_outbox_publish",
                        columnList = "status, next_retry_at, created_at"
                )
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    // 이벤트가 속한 도메인 대상 식별자
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Lob
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = OutboxStatus.PENDING;
        }
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.lastError = null;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.nextRetryAt = null;
        this.lastError = null;
    }

    public void markRetry(
            String errorMessage,
            LocalDateTime nextRetryAt
    ) {
        this.status = OutboxStatus.PENDING;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.lastError = errorMessage;
    }

    public void markFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
        this.nextRetryAt = null;
        this.lastError = errorMessage;
    }
}
