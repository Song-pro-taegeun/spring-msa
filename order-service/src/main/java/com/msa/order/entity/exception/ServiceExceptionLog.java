package com.msa.order.entity.exception;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "service_exception_logs",
        catalog = "msa_order",
        indexes = {
                @Index(
                        name = "idx_exception_logs_tenant_created_at",
                        columnList = "tenant_id, created_at"
                ),
                @Index(
                        name = "idx_exception_logs_status_created_at",
                        columnList = "exception_status, created_at"
                ),
                @Index(
                        name = "idx_exception_logs_trace_id",
                        columnList = "trace_id"
                )
        }
)
public class ServiceExceptionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exception_log_id", nullable = false)
    private Long exceptionLogId;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "method_name", nullable = false, length = 255)
    private String methodName;

    @Column(name = "exception_class", nullable = false, length = 500)
    private String exceptionClass;

    @Lob
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Lob
    @Column(name = "stack_trace", columnDefinition = "LONGTEXT")
    private String stackTrace;

    @Lob
    @Column(name = "request_payload", columnDefinition = "LONGTEXT")
    private String requestPayload;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "exception_status",
            nullable = false,
            length = 30
    )
    private ExceptionStatus exceptionStatus;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ServiceExceptionLog(
            String tenantId,
            String traceId,
            String methodName,
            String exceptionClass,
            String errorMessage,
            String stackTrace,
            String requestPayload
    ) {
        this.tenantId = tenantId;
        this.traceId = traceId;
        this.methodName = methodName;
        this.exceptionClass = exceptionClass;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
        this.requestPayload = requestPayload;
        this.exceptionStatus = ExceptionStatus.NEW;
    }

    public static ServiceExceptionLog create(
            String tenantId,
            String traceId,
            String methodName,
            String exceptionClass,
            String errorMessage,
            String stackTrace,
            String requestPayload
    ) {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("메서드명은 필수입니다.");
        }

        if (exceptionClass == null || exceptionClass.isBlank()) {
            throw new IllegalArgumentException("예외 클래스는 필수입니다.");
        }

        return new ServiceExceptionLog(
                tenantId,
                traceId,
                methodName,
                exceptionClass,
                errorMessage,
                stackTrace,
                requestPayload
        );
    }

    public void acknowledge() {
        this.exceptionStatus = ExceptionStatus.ACKNOWLEDGED;
    }

    public void close() {
        this.exceptionStatus = ExceptionStatus.CLOSED;
    }

    public void ignore() {
        this.exceptionStatus = ExceptionStatus.IGNORED;
    }
}