package com.msa.tenant.context;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * 테넌트 필터 생성
 * http 요청을 TenantContext로 연결하는 다리
 * X-Tenant-Id 추출 용도
 */
public class TenantFilter implements Filter {
    @Override
    public void doFilter(
            ServletRequest req,
            ServletResponse res,
            FilterChain chain) throws IOException, ServletException {

        try {
            HttpServletRequest request = (HttpServletRequest) req;
            String tenant = request.getHeader("X-Tenant-Id");

            if (tenant != null && !tenant.isBlank()) {
                TenantContext.set(tenant);
            }

            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}

