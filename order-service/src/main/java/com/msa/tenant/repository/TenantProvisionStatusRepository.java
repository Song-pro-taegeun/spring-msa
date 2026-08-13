package com.msa.tenant.repository;

import com.msa.tenant.entity.TenantProvisionStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantProvisionStatusRepository extends JpaRepository<TenantProvisionStatusEntity, String> {
}
