package com.msa.common.tenant.provision;

public enum TenantProvisionStep {
    STARTED,                        // status row 생성, 프로비저닝 시작
    CREATE_TENANT_USER,             // DB user 생성 단계
    CREATE_SCHEMA,                  // tenant schema 생성 단계
    GRANT_TENANT_PRIVILEGES,        // tenant DB user에게 tenant schema 권한 부여 단계
    INSERT_TENANT_DB_CREDENTIAL,    // msa_user.tenant_db_credential에 접속 정보 저장 단계
    GRANT_MASTER_PRIVILEGES,        // master user에게 tenant schema 권한 부여 단계
    FLYWAY_MIGRATE,                 // tenant schema에 Flyway migration 수행 단계
    INSERT_INITIAL_USER,            // tenant schema.users에 초기 사용자 insert 단계
    COMPLETED                       // 전체 완료
}