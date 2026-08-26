package com.msa.order.repository.master.tenant;

import com.msa.order.entity.master.tenant.TenantProvisionStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantProvisionStatusRepository extends JpaRepository<TenantProvisionStatusEntity, String> {
}
