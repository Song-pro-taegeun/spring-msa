package com.msa.tenant.credential;

import com.msa.common.credential.DbConnectionInfo;
import com.msa.common.credential.crypto.DbCredentialCrypto;
import com.msa.tenant.repository.TenantDbCredentialJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DbCredential Active Connection Info get
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantDbCredentialProvider {

    private final TenantDbCredentialJdbcRepository repository;
    private final DbCredentialCrypto crypto;

    public DbConnectionInfo getConnectionInfo(String tenantKey) {
        try{
            TenantDbCredential c = repository.findActive(tenantKey);
            String password = crypto.decrypt(c.getPasswordEnc(), c.getEncIv());

            return new DbConnectionInfo(
                    c.getServiceName() + "_" + c.getTenantKey(), // schema
                    c.getUsername(),
                    password
            );
        } catch (Exception e) {
            log.error("UserService 테넌트 스키마 찾기 실패: {}", e);
            throw new RuntimeException(e);
        }
    }
}
