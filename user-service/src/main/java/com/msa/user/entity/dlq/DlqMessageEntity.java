package com.msa.user.entity.dlq;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * DLQ 발송 정보 DB Entity
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dlq_message_info")
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
    @Column(name = "exception_class", nullable = false, length = 255)
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
}
