package com.msa.tenant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", unique = true, nullable = false)
    private String tenantKey;

    @Column(nullable = false)
    private String status;

    public Tenant(String tenantKey, String status) {
        this.tenantKey = tenantKey;
        this.status = status;
    }

    public void activate() {
        this.status = "ACTIVE";
    }

    public void fail() {
        this.status = "FAILED";
    }
}

