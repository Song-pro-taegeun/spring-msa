package com.msa.tenant.repository;

import com.msa.tenant.credential.TenantDbCredential;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 멀티테넌시 초기 단계에서 조회
 *  - 초기 단계에선 어떤 DB에 connection 할 지 hibernate는 모른다
 *  - 따라서 order master 스키마에서 테넌트 order connection 정보를 가져와야한다.
 *  - 즉, JPA를 우회하여 master 스키마에 커넥션 정보를 get
 */
@Repository
@RequiredArgsConstructor
public class TenantDbCredentialJdbcRepository {
    @Value("${spring.base-schema-name}")
    private String serviceName;
    private final JdbcTemplate jdbcTemplate;

    public TenantDbCredential findActive(String tenantKey) {
        String serviceTenantKey = serviceName.concat("_" + tenantKey);
        return jdbcTemplate.queryForObject(
                """
                SELECT tenant_db_crd_id, tenant_key, service_name,
                       username, password_enc, enc_iv
                FROM tenant_db_credential
                WHERE username = ?
                  AND status = 'ACTIVE'
                """,
                this::mapRow,
                serviceTenantKey
        );
    }

    private TenantDbCredential mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TenantDbCredential(
                rs.getLong("tenant_db_crd_id"),
                rs.getString("tenant_key"),
                rs.getString("service_name"),
                rs.getString("username"),
                rs.getBytes("password_enc"),
                rs.getBytes("enc_iv"),
                null, null, null
        );
    }
}
