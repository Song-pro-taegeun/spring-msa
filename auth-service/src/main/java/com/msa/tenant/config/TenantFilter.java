package com.msa.tenant.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

// 테넌트 필터 생성
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

