package com.msa.tenant.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.user.security.JwtAuthenticationFilter;
import com.msa.user.util.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 테넌트 필터 생성
 * http 요청을 TenantContext로 연결하는 다리
 * X-Tenant-Id 추출 용도
 */
@RequiredArgsConstructor
public class TenantFilter implements Filter {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtUtil jwtUtil;

    @Override
    public void doFilter(
            ServletRequest req,
            ServletResponse res,
            FilterChain chain) throws IOException, ServletException {

        try {
            HttpServletResponse response = (HttpServletResponse) res;
            HttpServletRequest request = (HttpServletRequest) req;
            String tenant = request.getHeader("X-Tenant-Id");
            String token = jwtAuthenticationFilter.extractToken(request);
            String tenantKey = jwtUtil.getTenantKey(token);

            // 토큰에 테넌트 존재하는 지 검증
            if(tenantKey == null || tenantKey.isBlank() ||
                    tenant == null || tenant.isBlank()){
                writeErrorResponse(response, request, 403, "요청헤더 또는 토큰에 테넌트 정보가 없습니다.");
                return;
            }

            // 테넌트 유효성 검증
            if(!Objects.equals(tenant, tenantKey)){
                writeErrorResponse(response, request, 403, "토큰의 테넌트 정보와 요청 헤더의 테넌트 정보가 일치하지 않습니다.");
                return;
            }

            TenantContext.set(tenant);
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Forbidden");
        body.put("message", message);
        body.put("status", status);
        body.put("path", request.getRequestURI());

        new ObjectMapper().writeValue(response.getWriter(), body);
    }
}

