package com.msa.tenant.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.common.entity.SecurityRole;
import com.msa.user.security.JwtAuthenticationFilter;
import com.msa.user.util.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

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

            // 1. Bearer 토큰 자체가 없으면 TenantFilter는 테넌트 검증을 하지 않음
            // TenantFilter는 인증/인가 담당이 아니라, 인증된 요청의 테넌트 검증 담당
            // 토큰이 없는데 JWT 파싱을 시도하면 예외가 발생하고, Swagger 같은 permitAll 요청도 막힘
            // 따라서 남은 Spring Security 필터 체인으로 넘겨서 permitAll/authenticated 여부를 Security가 판단하게 함
            String token = jwtAuthenticationFilter.extractToken(request);
            if (!StringUtils.hasText(token)) {
                chain.doFilter(req, res);
                return;
            }

            // 2. 토큰이 있다면, 인증 컨텍스트에 인증 객체가 존재하는지 확인
            // 요청 시 이전 필터인 jwtAuthenticationFilter 에서 SecurityContextHolder.getContext().setAuthentication(authentication) 해당 로직을 수행하여
            // 컨텍스트에 등록 함, 따라서 인증되지 않았다면, 테넌트 검증을 하지 않고 다음 필터에 위임
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                chain.doFilter(req, res);
                return;
            }

            // 3. 테넌트 ID 추출
            String tenant = request.getHeader("X-Tenant-Id");
            String tenantKey = jwtUtil.getTenantKey(token);

            // 3-1. 토큰에 테넌트 존재하는 지 검증
            if(tenantKey == null || tenantKey.isBlank() ||
                    tenant == null || tenant.isBlank()){
                writeErrorResponse(response, request, 403, "요청헤더 또는 토큰에 테넌트 정보가 없습니다.");
                return;
            }

            // 3-2. 테넌트 유효성 검증
            if(!Objects.equals(tenant, tenantKey) && !checkAdminRole(token)){
                writeErrorResponse(response, request, 403, "토큰의 테넌트 정보와 요청 헤더의 테넌트 정보가 일치하지 않습니다.");
                return;
            }

            TenantContext.set(tenant);
            chain.doFilter(req, res);
        } finally {
            // 반드시 테넌트 컨텍스트를 비워줘야함.
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

    private boolean checkAdminRole(String token) {
        return SecurityRole.ROLE_ADMIN.name()
                .equals(jwtUtil.getRole(token));
    }
}
