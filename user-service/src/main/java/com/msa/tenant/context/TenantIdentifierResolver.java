package com.msa.tenant.context;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;


// 테넌트 순서 3
// Hibernate가 현재의 tenant를 알 수 있게 정의해주는 클래스
// Hibernate는 내부에서 resolveCurrentTenantIdentifier() 함수를 직접 호출한다.(서버에서 직접 호출 X)
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {
    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.get() != null
                ? TenantContext.get()
                : "msa_user";
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}

