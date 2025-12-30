package com.msa.auth.entity.tenant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_id")
    private Long id;

    @Column(name = "tenant_key", nullable = false, unique = true, length = 50)
    private String tenantKey;

    @Column(name = "status", nullable = false)
    private String status;

    public void activate() {
        this.status = "ACTIVE";
    }
    public void fail() {
        this.status = "FAILED";
    }
}

