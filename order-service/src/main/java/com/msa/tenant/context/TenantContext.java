package com.msa.tenant.context;

/**
 * 요청별 tenant 저장소
 * 테넌트 컨텍스트 생성(스키마 동적 변경을 위한 쓰레드)
 *  - MVC 요청 -> 하나의 쓰레드
 *  - Hibernate 내부에서도 동일 스레드 사용
 * TenantFilter set 용도
 * TenantIdentifierResolver get 용도
 * 종료 시 clear
 */
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String tenant) {
        CURRENT.set(tenant);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    private TenantContext() {}
}

