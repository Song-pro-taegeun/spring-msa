package com.msa.order.entity.master.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "inbox_event",
        indexes = {
                @Index(
                        name = "idx_inbox_event_processed_at",
                        columnList = "processed_at"
                )
        }
)
public class InboxEvent {
    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    private InboxEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }

    public static InboxEvent create(String eventId, String eventType) {
        if (eventId == null) {
            throw new IllegalArgumentException("이벤트 ID는 필수입니다.");
        }

        if (eventType == null) {
            throw new IllegalArgumentException("이벤트 종류는 필수입니다.");
        }

        return new InboxEvent(eventId, eventType);
    }
}
