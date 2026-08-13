package com.msa.tenant.context;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;


/**
 * Hibernate가 현재의 tenant를 알 수 있게 정의해주는 클래스
 * Hibernate는 내부에서 resolveCurrentTenantIdentifier() 함수를 직접 호출한다.(Hibernate 내부에서 호출)
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {
    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.get() != null
                ? TenantContext.get()
                : "msa_order";
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}

