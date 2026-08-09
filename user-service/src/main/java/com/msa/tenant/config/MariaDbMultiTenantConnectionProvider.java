package com.msa.tenant.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.msa.common.credential.DbConnectionInfo;
import com.msa.tenant.credential.TenantDbCredentialProvider;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;


/**
 * tenant값을 통해 직접 DB 커넥션을 바꾸는 용도
 * 멀티테넌시의 핵심 부
 * Hibernate 내부에서 쿼리를 실행 전 getConnection("tenantA")를 호출한다.
 */
@Slf4j
@RequiredArgsConstructor
public class MariaDbMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {
    // HikariDataSource 캐시 사용 이유 -> 요청마다 HikariDataSource를 생성하지 않고, 캐시에 저장되어 있는 DataSource를 재사용한다.
    private final Cache<String, HikariDataSource> cache =
            Caffeine.newBuilder()
                    // 캐시에 HikariDataSource를 최대 12개만 보관
                    // 초과되는 테넌트가 들어오면 Caffeine이 사용 빈도와 최근 사용 이력을 바탕으로 기존 항목을 제거
                    // 단, 활성 상태에서 제거된 DataSource는 즉시 닫지 않으므로
                    // 실제 살아 있는 DataSource가 일시적으로 12개를 초과할 수 있음
                    .maximumSize(12)
                    // 마지막으로 캐시에 접근한 후 5분 동안 다시 사용되지 않은 테넌트 풀을 만료
                    // 해당 테넌트가 계속 사용되면 만료 시간이 다시 5분 뒤로 연장
                    .expireAfterAccess(Duration.ofMinutes(5))
                    // 캐시 항목이 제거될 때 호출되는 콜백
                    .removalListener(
                            (String tenant,
                             HikariDataSource ds,
                             RemovalCause cause) -> {
                                if (ds != null && canClose(ds)) {
                                    closeDataSource(ds);
                                }
                            }
                    )
                    .build();

    private final TenantDbCredentialProvider credentialProvider;
    private final DataSource masterDataSource;

    @Value("${spring.base-datasource.url}")
    private String mariaDbBaseUrl;

    @Value("${spring.base-schema-name}")
    private String baseSchemaName;


    @Override
    public Connection getConnection(String tenant) throws SQLException {
        log.debug("getConnection tenant={}", tenant);

        // 요청 시 현재 tenant 정보가 baseSchemaName와 같다면, masterDataSource 사용
        if (baseSchemaName.equals(tenant)) {
            return masterDataSource.getConnection();
        }

        // 아니라면 테넌트 HikariDataSource 생성 후 연결
        HikariDataSource ds = cache.get(
                tenant,
                this::createTenantDataSource
        );
        return ds.getConnection();
    }

    // 실제 테넌트 커넥션 DataSource 생성 부
    private HikariDataSource createTenantDataSource(String tenantKey) {
        DbConnectionInfo info = credentialProvider.getConnectionInfo(tenantKey);

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(mariaDbBaseUrl + info.schema());
        ds.setUsername(info.username());
        ds.setPassword(info.password());

        // 커넥션 최대 한도를 2로 설정 시 캐시는 해당 값을 나눠서 세팅해줘야 한다.(현재 12, 2로 설정 시 Caffeine 캐시 보관량은 6으로 설정)
        // 현재 DB 인스턴스 자원은 151 max_connections 이다.
        // 5개의 서비스 활성화 시 안정적으로 확보할 수 있는 커넥션 예산은 총합 85개 정도 이고, 서비스별 마스터 커넥션을 제외 한 테넌트 별 커넥션의 총합은 60으로 구성했다.
        // 따라서, User Service에서 사용될 수 있는 커넥션 예산은 12개 이다.
        ds.setMaximumPoolSize(1);
        ds.setMinimumIdle(0); // HikariCP는 minimumIdle를 명시하지 않으면 기본적으로 maximumPoolSize과 동일하게 취급함.
        ds.setIdleTimeout(300_000); // 유효 커넥션이 300초 이상 사용되지 않으면 풀 내부의 물리 DB 커넥션을 종료
        ds.setMaxLifetime(1_800_000); // 커넥션의 최대 수명
        ds.setConnectionTimeout(5_000); // 커넥션 풀이 가득찼을 때 대기하는 시간, 5초

        ds.setPoolName("tenant-" + tenantKey);

        return ds;
    }

    // removalListener에서 커넥션 풀을 닫기 전 활성 커넥션이 없는 경우에만 풀을 닫아야 함.
    private boolean canClose(HikariDataSource ds) {
        return ds.getHikariPoolMXBean() != null
                && ds.getHikariPoolMXBean().getActiveConnections() == 0;
    }

    // 애플리케이션 정상 종료 직전에 캐시에 남아 있는
    // 모든 테넌트 Hikari 풀과 물리 DB 연결을 종료
    @PreDestroy // Bean 객체가 소멸되기 직전에 호출
    public void closeDataSources() {
        cache.asMap()
                .values()
                .forEach(this::closeDataSource);

        cache.invalidateAll();
        cache.cleanUp();
    }

    private void closeDataSource(HikariDataSource ds) {
        // 캐시에서 참조만 제거하는 것이 아니라 해당 테넌트의 Hikari 풀과 풀 내부 DB 연결까지 종료
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
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

