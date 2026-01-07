package com.msa.tenant.repository;

import com.msa.tenant.credential.TenantDbCredential;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
@RequiredArgsConstructor
public class TenantDbCredentialJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    public TenantDbCredential findActive(String tenantId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT tenant_db_crd_id, tenant_id, service_name,
                       username, password_enc, enc_iv
                FROM tenant_db_credential
                WHERE username = ?
                  AND status = 'ACTIVE'
                """,
                this::mapRow,
                tenantId
        );
    }

    private TenantDbCredential mapRow(ResultSet rs, int rowNum)
            throws SQLException {

        return new TenantDbCredential(
                rs.getLong("tenant_db_crd_id"),
                rs.getString("tenant_id"),
                rs.getString("service_name"),
                rs.getString("username"),
                rs.getBytes("password_enc"),
                rs.getBytes("enc_iv"),
                null, null, null
        );
    }
}
