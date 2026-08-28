package com.msa.user.entity.dlq;

import com.msa.common.kafka_event.DlqMessage;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * DLQ 발송 정보 DB Entity
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "dlq_message_info",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dlq_message_original_topic_partition_offset",
                columnNames = {"original_topic", "original_partition", "original_offset"}
        )
)
public class DlqMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dlq_info_id")
    private Long id;

     // Kafka 원본 정보
    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    @Column(name = "original_partition", nullable = false)
    private Integer originalPartition;

    @Column(name = "original_offset", nullable = false)
    private Long originalOffset;

    @Column(name = "kafka_message_key", length = 255)
    private String kafkaMessageKey;

    // 실패 원인 정보
    @Column(name = "exception_class", length = 255)
    private String exceptionClass;

    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;

    @Lob
    @Column(name = "stack_trace", columnDefinition = "LONGTEXT")
    private String stackTrace;

    // 메시지 Payload
    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    // DLQ 처리 상태
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DlqStatus status = DlqStatus.NEW;

    // 감사 / 운영 정보
    @CreationTimestamp
    @Column(name = "create_at", updatable = false)
    private LocalDateTime createAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", length = 100)
    private DlqCreateBy createdBy;

    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;

    public static DlqMessageEntity fromKafkaDlq(
            DlqMessage<?> dlq,
            String kafkaMessageKey,
            String payloadJson
    ) {
        return DlqMessageEntity.builder()
                .originalTopic(dlq.getOriginalTopic())
                .originalPartition(dlq.getOriginalPartition())
                .originalOffset(dlq.getOriginalOffset())
                .kafkaMessageKey(kafkaMessageKey)
                .exceptionClass(dlq.getExceptionClass())
                .exceptionMessage(dlq.getExceptionMessage())
                .stackTrace(dlq.getStackTrace())
                .payloadJson(payloadJson)
                .status(DlqStatus.NEW)
                .createdBy(DlqCreateBy.CONSUMER)
                .build();
    }

    public void markReplayed() {
        // 추후 DLQ 통계 용으로 null 처리 하지 않음
        // this.exceptionClass = null;
        // this.exceptionMessage = null;
        // this.stackTrace = null;

        this.status = DlqStatus.REPLAYED;
        this.replayedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = DlqStatus.FAILED;
        this.replayedAt = LocalDateTime.now();
    }

    public void markIgnored() {
        this.status = DlqStatus.IGNORED;
        this.replayedAt = LocalDateTime.now();
    }
}
