package com.msa.tenant.config;

import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 테넌트 순서 4
// tenant값을 통해 직접 DB 커넥션을 바꾸는 용도
// 멀티테넌시의 핵심 부
// Hibernate 내부에서 쿼리를 실행 전 getConnection("tenantA")를 호출한다.
public class MariaDbMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {
    // DataSource 캐시 사용 이유 -> 요청마다 DataSource를 생성하지 않고, 커넥션 풀을 재사용한다.
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    @Override
    public Connection getConnection(String tenant) throws SQLException {
        DataSource ds = dataSources.computeIfAbsent(
                tenant,
                this::createDataSource
        );
        return ds.getConnection();
    }

    private DataSource createDataSource(String tenant) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mariadb://localhost:3306/" + tenant);
        ds.setUsername("msa_auth_user");
        ds.setPassword("1234");
        return ds;
    }

    // 테넌트가 할당되지 않았을 때 기본으로 사용하는 스키마
    @Override
    public Connection getAnyConnection() throws SQLException {
        return getConnection("msa_auth");
    }

    @Override public void releaseConnection(String t, Connection c) throws SQLException { c.close(); }
    @Override public void releaseAnyConnection(Connection c) throws SQLException { c.close(); }
    @Override public boolean supportsAggressiveRelease() { return false; }
    @Override public boolean isUnwrappableAs(Class<?> unwrapType) { return false; }
    @Override public <T> T unwrap(Class<T> unwrapType) { return null; }
}

