package com.msa.tenant.credential;

import com.msa.common.credential.DbConnectionInfo;
import com.msa.common.credential.crypto.DbCredentialCrypto;
import com.msa.tenant.repository.TenantDbCredentialJdbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DbCredential Active Connection Info get
 */
@Component
@RequiredArgsConstructor
public class TenantDbCredentialProvider {

    private final TenantDbCredentialJdbcRepository repository;
    private final DbCredentialCrypto crypto;

    public DbConnectionInfo getConnectionInfo(String tenantId) {
        TenantDbCredential c = repository.findActive(tenantId);
        String password = crypto.decrypt(c.getPasswordEnc(), c.getEncIv());

        return new DbConnectionInfo(
                c.getServiceName() + "_" + c.getTenantId(), // schema
                c.getUsername(),
                password
        );
    }
}
