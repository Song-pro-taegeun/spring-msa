package com.msa.user.service.user;

import com.msa.common.kafka_event.TenantProvisionedEvent;
import com.msa.common.credential.crypto.DbCredentialCrypto;
import com.msa.common.tenant.provision.TenantProvisionStatus;
import com.msa.common.tenant.provision.TenantProvisionStep;
import com.msa.tenant.entity.TenantProvisionStatusEntity;
import com.msa.tenant.repository.TenantProvisionStatusRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    INSERT INTO tenant_db_credential (
        tenant_key,
        service_name,
        username,
        password_enc,
        enc_iv,
        status
    )
    VALUES (?, ?, ?, ?, ?, 'ACTIVE')
    ON DUPLICATE KEY UPDATE
        username = VALUES(username),
        password_enc = VALUES(password_enc),
        enc_iv = VALUES(enc_iv),
        status = 'ACTIVE'
""";

    // 트랜잭션 주석의 이유
    // @Transactional이 기본 JpaTransactionManager를 타면서 멀티테넌트 Hibernate 커넥션(getConnection -> findActive)을 먼저 연다.
    // provision()은 JPA가 아니라 baseDataSource, DDL, GRANT, Flyway처럼 직접 JDBC로 처리하는 작업이라 JPA 트랜잭션과 맞지 않는다.
    // DDL/권한/Flyway 작업은 하나의 @Transactional로 원자성 보장이 어렵기 때문에, 트랜잭션보다 멱등성/상태관리/재처리로 안정성을 잡는 게 맞다
    // @Transactional
    public void provision(TenantProvisionedEvent event) {
        String tenantSchema = baseSchemaName + "_" + event.getTenantKey();
        String password = dbCredentialCrypto.decrypt(event.getPasswordEnc(), event.getEncIv());

        // 멱등성 관련
        // 기존 프로비저닝 row가 있는지 먼저 확인
        TenantProvisionStatusEntity entity = tenantProvisionStatusRepository
                .findById(event.getTenantKey())
                .orElse(null);

        // 1. 해당 테넌트의 프로비져닝 상태가 모두 완료된 상태라면 하기 비즈니스 로직을 실행하지 않는다.
        if (entity != null && entity.getStatus() == TenantProvisionStatus.COMPLETED) {
            return;
        }

        // 재시도 된 이벤트라면 현재까지 저장 된 상태 값을 읽음
        TenantProvisionStatus savedStatus =
                entity == null ? TenantProvisionStatus.PROCESSING : entity.getStatus();

        // 재시도 된 이벤트라면 현재까지 저장 된 단계 값을 읽음
        TenantProvisionStep savedStep =
                entity == null ? TenantProvisionStep.STARTED : entity.getLastStep();

        TenantProvisionStep currentStep = savedStep;
        try {
            // 2. 테넌트 프로비져닝 시작 row 기록
            if (entity == null) {
                entity = TenantProvisionStatusEntity.builder()
                        .tenantKey(event.getTenantKey())
                        .build();
                startProvision(event, entity);
            }

            // 3. tenant DB 유저 생성
            if (shouldRun(savedStatus, savedStep, TenantProvisionStep.CREATE_TENANT_USER)){
                currentStep = TenantProvisionStep.CREATE_TENANT_USER;
                updateProvisionStep(event, currentStep);
                createTenantUserIfNotExists(tenantSchema, password);
            }

            // 4. 스키마 생성
            if (shouldRun(savedStatus, savedStep, TenantProvisionStep.CREATE_SCHEMA)){
                currentStep = TenantProvisionStep.CREATE_SCHEMA;
                updateProvisionStep(event, currentStep);
                createSchemaIfNotExists(tenantSchema);
            }

            // 5. 스키마에 테넌트 유저 권한 등록
            if (shouldRun(savedStatus, savedStep, TenantProvisionStep.GRANT_TENANT_PRIVILEGES)){
                currentStep = TenantProvisionStep.GRANT_TENANT_PRIVILEGES;
                updateProvisionStep(event, currentStep);
                grantTenantPrivileges(tenantSchema);
            }

            // 6. 마스터 스키마에 테넌트 별 DB 커넥션 계정 정보 넣기
            if (shouldRun(savedStatus, savedStep, TenantProvisionStep.INSERT_TENANT_DB_CREDENTIAL)){
                currentStep = TenantProvisionStep.INSERT_TENANT_DB_CREDENTIAL;
                updateProvisionStep(event, currentStep);
                insertTenantDbCredential(event, tenantSchema);
            }

            // 7. master-datasource.username에 해당 테넌트 권한 부여
            if (shouldRun(savedStatus, savedStep, TenantProvisionStep.GRANT_MASTER_PRIVILEGES)){
                currentStep = TenantProvisionStep.GRANT_MASTER_PRIVILEGES;
                updateProvisionStep(event, currentStep);
                grantMasterPrivileges(tenantSchema);
            }

            // 8. Flyway를 통한 마이그레이션 진행
            if (shouldRun(savedStatus, savedStep, TenantProvisionStep.FLYWAY_MIGRATE)){
                currentStep = TenantProvisionStep.FLYWAY_MIGRATE;
                updateProvisionStep(event, currentStep);
                runFlyway(tenantSchema, password);

                // 익셉션 발생 시킨 후 skip로직 및 재시도 로직 정상 수행하는지 체크
                // int a = 1/0;
            }

            // 9. envent payload 데이터 추가
            if (shouldRun(savedStatus, savedStep, TenantProvisionStep.INSERT_INITIAL_USER)){
                currentStep = TenantProvisionStep.INSERT_INITIAL_USER;
                updateProvisionStep(event, currentStep);
                insertInitialUser(tenantSchema, event, password);
            }

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
    private void startProvision(TenantProvisionedEvent event, TenantProvisionStatusEntity entity){
        commonProvisionStepUpdate(entity, event, TenantProvisionStatus.PROCESSING, TenantProvisionStep.STARTED, null);
    }

    /**
     * 테넌트 프로비저닝 완료 기록
     */
    private void completeProvision(TenantProvisionedEvent event, TenantProvisionStep step) {
        TenantProvisionStatusEntity entity = tenantProvisionStatusRepository
                .findById(event.getTenantKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Provision status row not found. tenantKey=" + event.getTenantKey()
                ));

        commonProvisionStepUpdate(entity, event, TenantProvisionStatus.COMPLETED, step, null);
    }

    /**
     * 프로비저닝 진행 단계 기록
     */
    private void updateProvisionStep(TenantProvisionedEvent event, TenantProvisionStep step) {
        TenantProvisionStatusEntity entity = tenantProvisionStatusRepository
                .findById(event.getTenantKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Provision status row not found. tenantKey=" + event.getTenantKey()
                ));

        commonProvisionStepUpdate(entity, event, TenantProvisionStatus.PROCESSING, step, null);
    }

    /**
     * 테넌트 프로비저닝 실패 기록
     */
    private void failProvision(
            TenantProvisionedEvent event,
            TenantProvisionStep step,
            Exception exception
    ) {
        TenantProvisionStatusEntity entity = tenantProvisionStatusRepository
                .findById(event.getTenantKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Provision status row not found. tenantKey=" + event.getTenantKey()
                ));

        commonProvisionStepUpdate(entity, event, TenantProvisionStatus.FAILED, step, exception);
    }

    /**
     * 시작 / 완료 / 수정 / 실패 공통 업데이트 비즈니스 로직
     * @param entity tenant provision 객체
     * @param event 이벤트 객체
     * @param provisionStatus 처리할 상태 값
     * @param step 현재 step
     * @param exception exception
     */
    private void commonProvisionStepUpdate(
            TenantProvisionStatusEntity entity,
            TenantProvisionedEvent event,
            TenantProvisionStatus provisionStatus,
            TenantProvisionStep step,
            Exception exception
    ){
        // entity.setEventId(event.getEventId());
        entity.setStatus(provisionStatus);
        entity.setLastStep(step);
        entity.setErrorMessage(exception != null ? exception.getMessage() : null);

        tenantProvisionStatusRepository.save(entity);
    }

    /**
     * tenant Provision 전 단계 체크
     */
    private boolean shouldRun(
            TenantProvisionStatus status,
            TenantProvisionStep savedStep,
            TenantProvisionStep targetStep
    ) {
        if (status == TenantProvisionStatus.COMPLETED) {
            return false;
        }

        if (status == TenantProvisionStatus.FAILED
                || status == TenantProvisionStatus.PROCESSING) {
            return targetStep.isAfterOrSame(savedStep);
        }

        return targetStep.isAfter(savedStep);
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
        try (
            HikariDataSource tenantDs = tenantDataSource(tenantSchema, password)
        ) {
            Flyway.configure()
                    .dataSource(tenantDs)
                    .schemas(tenantSchema)
                    .locations("classpath:db/migration/tenant")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
        }
    }

    /**
     * 이벤트 payload 데이터 추가
     * @param tenantSchema
     * @param event
     */
    private void insertInitialUser(String tenantSchema, TenantProvisionedEvent event, String password) {
        // try-with-resources
        // ps.close();
        // conn.close();
        // 를 하지 않아도 try 블록이 끝날 때 Java가 자동으로 close 호출, 그렇지 않으면 finally 에 직접 명시해야함.
        try (
                HikariDataSource tenantDs = tenantDataSource(tenantSchema, password);
                Connection conn = tenantDs.getConnection();
                PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO users (user_id, user_name)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE
                        user_name = VALUES(user_name)
                """)
        ) {
            ps.setString(1, event.getUserId());
            ps.setString(2, event.getUserName());
            ps.executeUpdate();

        } catch (Exception e) {
            throw new IllegalStateException("Initial user insert failed", e);
        }
    }

    private HikariDataSource tenantDataSource(
            String tenantSchema,
            String password
    ) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(baseJdbcUrl + tenantSchema);
        ds.setUsername(tenantSchema);
        ds.setPassword(password);
        ds.setMaximumPoolSize(1);
        ds.setMinimumIdle(0);
        ds.setPoolName("provision-" + tenantSchema);
        return ds;
    }
}

