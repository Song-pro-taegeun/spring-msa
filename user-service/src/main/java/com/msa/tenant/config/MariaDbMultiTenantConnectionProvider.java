package com.msa.tenant.config;

import com.msa.common.credential.DbConnectionInfo;
import com.msa.tenant.credential.TenantDbCredentialProvider;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * tenant값을 통해 직접 DB 커넥션을 바꾸는 용도
 * 멀티테넌시의 핵심 부
 * Hibernate 내부에서 쿼리를 실행 전 getConnection("tenantA")를 호출한다.
 */
@RequiredArgsConstructor
public class MariaDbMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {
    // DataSource 캐시 사용 이유 -> 요청마다 DataSource를 생성하지 않고, 커넥션 풀을 재사용한다.
    private final Map<String, DataSource> cache = new ConcurrentHashMap<>();

    private final TenantDbCredentialProvider credentialProvider;
    private final String serviceName;
    private final DataSource masterDataSource;

    @Override
    public Connection getConnection(String tenant) throws SQLException {
        // 1. 데이터 소스에 테넌트 키가 있으면 아무런 작업을 하지 않고 기존에 존재하는 Key의 Value를 리턴
        // 2. 없으면 함수 호출
        // - 서비스 시작 시 기본 데이터 소스는 getAnyConnection()를 통해 msa_user로 할당
        // - DataSource 에 등록 되어 있음
        // - 등록된 키는 함수를 호출하지 않음
        // 3. 즉 msa_user로 첫 요청 시에는 해당 키가 없기 때문에, createDataSource 를 실행하게 됨.
        System.out.println("getConnection tenant=" + tenant);
        DataSource ds = cache.computeIfAbsent(
                tenant,
                this::createTenantDataSource
        );
        return ds.getConnection();
    }

    // 실제 테넌트 커넥션 DataSource 생성 부
    private DataSource createTenantDataSource(String tenantKey) {
        DbConnectionInfo info = credentialProvider.getConnectionInfo(tenantKey);

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(
                "jdbc:mariadb://localhost:3306/" + info.schema()
        );
        ds.setUsername(info.username());
        ds.setPassword(info.password());
        ds.setMaximumPoolSize(10);
        ds.setPoolName("tenant-" + tenantKey);

        return ds;
    }

    // 테넌트가 할당되지 않았을 때 기본으로 사용하는 스키마
    @Override
    public Connection getAnyConnection() throws SQLException {
        return masterDataSource.getConnection();
    }

    @Override public void releaseConnection(String t, Connection c) throws SQLException { c.close(); }
    @Override public void releaseAnyConnection(Connection c) throws SQLException { c.close(); }
    @Override public boolean supportsAggressiveRelease() { return false; }
    @Override public boolean isUnwrappableAs(Class<?> unwrapType) { return false; }
    @Override public <T> T unwrap(Class<T> unwrapType) { return null; }
}

