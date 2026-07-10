package com.msa.user.service.user;

import com.msa.common.kafka_event.TenantProvisionedEvent;
import com.msa.common.credential.crypto.DbCredentialCrypto;
import com.msa.common.tenant.provision.TenantProvisionStatus;
import com.msa.common.tenant.provision.TenantProvisionStep;
import com.msa.tenant.entity.TenantProvisionStatusEntity;
import com.msa.tenant.repository.TenantProvisionStatusRepository;
import jakarta.transaction.Transactional;
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
    private final DbCredentialCrypto dbCredentialCrypto;
    private final TenantProvisionStatusRepository tenantProvisionStatusRepository;

    public TenantSchemaService(
            @Qualifier("baseDataSource") DataSource baseDataSource,
            DbCredentialCrypto dbCredentialCrypto,
            TenantProvisionStatusRepository tenantProvisionStatusRepository
    ) {
        this.baseDataSource = baseDataSource;
        this.dbCredentialCrypto = dbCredentialCrypto;
        this.tenantProvisionStatusRepository = tenantProvisionStatusRepository;
    }

    @Value("${spring.base-datasource.url}")
    private String baseJdbcUrl;

    @Value("${spring.base-schema-name}")
    private String baseSchemaName;

    @Value("${spring.master-datasource.username}")
    private String masterUsername;

    private static final String INSERT_TENANT_DB_CREDENTIAL_SQL = """
    INSERT INTO msa_user.tenant_db_credential (
        tenant_key,
        service_name,
        username,
        password_enc,
        enc_iv,
        status
    )
    VALUES (?, ?, ?, ?, ?, 'ACTIVE')
""";

    // 트랜잭션 주석의 이유
    // @Transactional이 기본 JpaTransactionManager를 타면서 멀티테넌트 Hibernate 커넥션(getConnection -> findActive)을 먼저 연다.
    // provision()은 JPA가 아니라 baseDataSource, DDL, GRANT, Flyway처럼 직접 JDBC로 처리하는 작업이라 JPA 트랜잭션과 맞지 않는다.
    // DDL/권한/Flyway 작업은 하나의 @Transactional로 원자성 보장이 어렵기 때문에, 트랜잭션보다 멱등성/상태관리/재처리로 안정성을 잡는 게 맞다
    // @Transactional
    public void provision(TenantProvisionedEvent event) {
        // 멱등성 관련
        // 1. 해당 테넌트의 프로비져닝 상태가 모두 완료된 상태라면 하기 비즈니스 로직을 실행하지 않는다.
        if (tenantProvisionStatusRepository.existsByTenantKeyAndStatus(
                event.getTenantKey(),
                TenantProvisionStatus.COMPLETED
        )) {
            return;
        }

        String tenantSchema = baseSchemaName + "_" + event.getTenantKey();
        String password = dbCredentialCrypto.decrypt(event.getPasswordEnc(), event.getEncIv());

        TenantProvisionStep currentStep = TenantProvisionStep.STARTED;
        try {
            // 2. 테넌트 프로비져닝 시작 row 기록
            startProvision(event, currentStep);

            // 3. tenant DB 유저 생성
            currentStep = TenantProvisionStep.CREATE_TENANT_USER;
            createTenantUserIfNotExists(tenantSchema, password);

            // 4. 스키마 생성
            currentStep = TenantProvisionStep.CREATE_SCHEMA;
            createSchemaIfNotExists(tenantSchema);

            // 5. 스키마에 테넌트 유저 권한 등록
            currentStep = TenantProvisionStep.GRANT_TENANT_PRIVILEGES;
            grantTenantPrivileges(tenantSchema);

            // 6. 마스터 스키마에 테넌트 별 DB 커넥션 계정 정보 넣기
            currentStep = TenantProvisionStep.INSERT_TENANT_DB_CREDENTIAL;
            insertTenantDbCredential(event, tenantSchema);

            // 7. master-datasource.username에 해당 테넌트 권한 부여
            currentStep = TenantProvisionStep.GRANT_MASTER_PRIVILEGES;
            grantMasterPrivileges(tenantSchema);

            // 8. Flyway를 통한 마이그레이션 진행
            currentStep = TenantProvisionStep.FLYWAY_MIGRATE;
            
            runFlyway(tenantSchema, password);

            // 9. envent payload 데이터 추가
            currentStep = TenantProvisionStep.INSERT_INITIAL_USER;
            insertInitialUser(tenantSchema, event, password);

            // 10. Provision 완료
            currentStep = TenantProvisionStep.COMPLETED;
            completeProvision(event, currentStep);

        } catch (Exception e) {
            failProvision(event, currentStep, e);
            throw e;
        }
    }

    /**
     *  테넌트 프로비져닝 시작 row 기록
     */
    private void startProvision(TenantProvisionedEvent event, TenantProvisionStep currentStep){
        String tenantKey = event.getTenantKey();
        String eventId = event.getEventId();

        TenantProvisionStatusEntity entity = tenantProvisionStatusRepository
                .findById(tenantKey)
                .orElseGet(() ->  // Optional이 비어 있을 때 실행할 함수
                        TenantProvisionStatusEntity.builder()
                                .tenantKey(event.getTenantKey())
                                .build());

        entity.setEventId(eventId);
        entity.setStatus(TenantProvisionStatus.PROCESSING);
        entity.setLastStep(currentStep);
        entity.setErrorMessage(null);
        tenantProvisionStatusRepository.save(entity);
    }

    /**
     * 테넌트 프로비저닝 완료 기록
     */
    private void completeProvision(TenantProvisionedEvent event, TenantProvisionStep currentStep) {
        TenantProvisionStatusEntity entity = tenantProvisionStatusRepository
                .findById(event.getTenantKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Provision status row not found. tenantKey=" + event.getTenantKey()
                ));

        entity.setEventId(event.getEventId());
        entity.setStatus(TenantProvisionStatus.COMPLETED);
        entity.setLastStep(currentStep);
        entity.setErrorMessage(null);

        tenantProvisionStatusRepository.save(entity);
    }

    /**
     * 테넌트 프로비저닝 실패 기록
     */
    private void failProvision(
            TenantProvisionedEvent event,
            TenantProvisionStep failedStep,
            Exception exception
    ) {
        TenantProvisionStatusEntity entity = tenantProvisionStatusRepository
                .findById(event.getTenantKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Provision status row not found. tenantKey=" + event.getTenantKey()
                ));

        entity.setEventId(event.getEventId());
        entity.setStatus(TenantProvisionStatus.FAILED);
        entity.setLastStep(failedStep);
        entity.setErrorMessage(exception.getMessage());

        tenantProvisionStatusRepository.save(entity);
    }

    /**
     * tenant DB 유저 생성 (없으면)
     */
    private void createTenantUserIfNotExists(String tenantSchema, String password) {
        try (
            Connection conn = baseDataSource.getConnection();
            Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE USER IF NOT EXISTS '%s'@'%%'
                IDENTIFIED BY '%s'
            """.formatted(tenantSchema, password));

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
     *  마스터 스키마에 테넌트 별 DB 커넥션 계정 정보 넣기
     */
    private void insertTenantDbCredential(
            TenantProvisionedEvent event,
            String tenantSchema
    ) {
        try (
                Connection conn = baseDataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(INSERT_TENANT_DB_CREDENTIAL_SQL)
        ) {
            ps.setString(1, event.getTenantKey());
            ps.setString(2, baseSchemaName);
            ps.setString(3, tenantSchema);
            ps.setBytes(4, event.getPasswordEnc());
            ps.setBytes(5, event.getEncIv());
            ps.executeUpdate();

        } catch (Exception e) {
            throw new IllegalStateException("Failed to insert tenant_db_credential for tenantId=" + event.getTenantKey(), e);
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
    private void runFlyway(String tenantSchema, String password) {
        DataSource tenantDs = tenantDataSource(tenantSchema, password);
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
    private void insertInitialUser(String tenantSchema, TenantProvisionedEvent event, String password) {
        DataSource tenantDs = tenantDataSource(tenantSchema, password);
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

    private DataSource tenantDataSource(String tenantSchema, String password) {
        return DataSourceBuilder.create()
                .url(baseJdbcUrl + tenantSchema)
                .username(tenantSchema)
                .password(password)
                .build();
    }
}

