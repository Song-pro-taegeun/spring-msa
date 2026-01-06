package com.msa.user.repository.tenant;

import com.msa.user.entity.tenant.TenantDbCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantDbCredentialRepository extends JpaRepository<TenantDbCredential, Long> {
}
