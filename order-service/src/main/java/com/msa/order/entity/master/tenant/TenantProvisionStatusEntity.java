package com.msa.order.entity.master.tenant;

import com.msa.common.tenant.provision.TenantProvisionStatus;
import com.msa.common.tenant.provision.TenantProvisionStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 테넌트 프로비저닝 진행 상태 Entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_provision_status")
public class TenantProvisionStatusEntity {
    @Id
    @Column(name = "tenant_key", nullable = false, length = 50)
    private String tenantKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantProvisionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_step", length = 50)
    private TenantProvisionStep lastStep;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
