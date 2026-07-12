package com.msa.common.tenant.provision;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * enum의 oridinal() 함수를 사용하지 않기 위해 인스턴스 필드로 값을 지정함.
 */
@Getter
@RequiredArgsConstructor
public enum TenantProvisionStep {
    STARTED(0),                         // status row 생성, 프로비저닝 시작
    CREATE_TENANT_USER(10),             // DB user 생성 단계
    CREATE_SCHEMA(20),                  // tenant schema 생성 단계
    GRANT_TENANT_PRIVILEGES(30),        // tenant DB user에게 tenant schema 권한 부여 단계
    INSERT_TENANT_DB_CREDENTIAL(40),    // msa_user.tenant_db_credential에 접속 정보 저장 단계
    GRANT_MASTER_PRIVILEGES(50),        // master user에게 tenant schema 권한 부여 단계
    FLYWAY_MIGRATE(60),                 // tenant schema에 Flyway migration 수행 단계
    INSERT_INITIAL_USER(70),            // tenant schema.users에 초기 사용자 insert 단계
    COMPLETED(80);                      // 전체 완료

    private final int order;

    public boolean isAfter(TenantProvisionStep other) {
        return this.order > other.order;
    }

    public boolean isAfterOrSame(TenantProvisionStep other) {
        return this.order >= other.order;
    }
}