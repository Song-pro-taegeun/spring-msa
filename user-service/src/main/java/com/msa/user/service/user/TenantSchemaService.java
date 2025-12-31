package com.msa.user.service.user;

import com.msa.common.kafka_event.TenantProvisionedEvent;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.stereotype.Service;


import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Service
public class TenantSchemaService {
    private final DataSource baseDataSource;
    private static final String TENANT_PASSWORD = "1234";

    public TenantSchemaService(
            @Qualifier("baseDataSource") DataSource baseDataSource
    ) {
        this.baseDataSource = baseDataSource;
    }

    @Value("${spring.master-datasource.url}")
    private String baseJdbcUrl;

    @Value("${spring.master-datasource.username}")
    private String masterUsername;

    @Value("${spring.base-schema-name}")
    private String baseSchemaName;

    public void provision(TenantProvisionedEvent event) {
        String tenantSchema = baseSchemaName + "_" + event.getTenantKey();

        // 1. tenant DB 유저 생성
        createTenantUserIfNotExists(tenantSchema);

        // 2. 스키마 생성
        createSchemaIfNotExists(tenantSchema);

        // 3. 스키마에 테넌트 유저 권한 등록
        grantTenantPrivileges(tenantSchema);

        // 4. master-datasource.username에 해당 테넌트 권한 부여
        grantMasterPrivileges(tenantSchema);

        // 5. Flyway를 통한 마이그레이션 진행
        runFlyway(tenantSchema);

        // 6. envent payload 데이터 추가
        insertInitialUser(tenantSchema, event);
    }

    /**
     * tenant DB 유저 생성 (없으면)
     */
    private void createTenantUserIfNotExists(String tenantSchema) {
        try (
            Connection conn = baseDataSource.getConnection();
            Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE USER IF NOT EXISTS '%s'@'%%'
                IDENTIFIED BY '%s'
            """.formatted(tenantSchema, TENANT_PASSWORD));

        } catch (Exception e) {
            throw new IllegalStateException("Create DB user failed", e);
        }
    }

    /**
     * 동일한 스키마가 있는지 체크
     * 없으면 생성
     */
    private void createSchemaIfNotExists(String tenantSchema) {
        try (
            Connection conn = baseDataSource.getConnection();
            Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + tenantSchema);
        } catch (Exception e) {
            throw new IllegalStateException("Schema creation failed", e);
        }
    }

    /**
     * tenant schema 권한 부여 (user == schema)
     */
    private void grantTenantPrivileges(String tenantSchema) {
        try (
            Connection conn = baseDataSource.getConnection();
            Statement stmt = conn.createStatement()) {

            stmt.execute("""
                GRANT
                  SELECT, INSERT, UPDATE, DELETE,
                  CREATE, ALTER, INDEX
                ON %s.*
                TO '%s'@'%%'
            """.formatted(tenantSchema, tenantSchema));

        } catch (Exception e) {
            throw new IllegalStateException("Grant privileges failed", e);
        }
    }

    /**
     * master DB 유저에게 tenant schema 권한 부여
     */
    private void grantMasterPrivileges(String tenantSchema) {
        try (
            Connection conn = baseDataSource.getConnection();
            Statement stmt = conn.createStatement()) {

            stmt.execute("""
                GRANT
                  SELECT, INSERT, UPDATE, DELETE,
                  CREATE, ALTER, INDEX
                ON %s.*
                TO '%s'@'%%'
            """.formatted(tenantSchema, masterUsername));

        } catch (Exception e) {
            throw new IllegalStateException("Grant master privileges failed", e);
        }
    }

    /**
     * Flyway 마이그레이션 진행
     */
    private void runFlyway(String tenantSchema) {
        DataSource tenantDs = tenantDataSource(tenantSchema);
        Flyway flyway = Flyway.configure()
                .dataSource(tenantDs)
                .schemas(tenantSchema)
                .locations("classpath:db/migration/tenant")
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();
    }

    /**
     * 이벤트 payload 데이터 추가
     * @param tenantSchema
     * @param event
     */
    private void insertInitialUser(String tenantSchema, TenantProvisionedEvent event) {
        DataSource tenantDs = tenantDataSource(tenantSchema);
        try (
                Connection conn = tenantDs.getConnection();
                PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO users (user_id, user_name)
                    VALUES (?, ?)
                """)
        ) {
            ps.setString(1, event.getUserId());
            ps.setString(2, event.getUserName());
            ps.executeUpdate();

        } catch (Exception e) {
            throw new IllegalStateException("Initial user insert failed", e);
        }
    }

    private DataSource tenantDataSource(String tenantSchema) {
        return DataSourceBuilder.create()
                .url(baseJdbcUrl + tenantSchema)
                .username(tenantSchema)
                .password(TENANT_PASSWORD)
                .build();
    }
}

