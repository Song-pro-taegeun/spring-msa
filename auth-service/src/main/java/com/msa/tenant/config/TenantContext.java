package com.msa.tenant.config;

// 테넌트 컨텍스트 생성(스키마 동적 변경을 위한 쓰레드)
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

