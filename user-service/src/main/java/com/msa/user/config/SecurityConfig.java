package com.msa.user.config;
import com.msa.tenant.context.TenantFilter;
import com.msa.user.security.CustomAccessDeniedHandler;
import com.msa.user.security.CustomAuthenticationEntryPoint;
import com.msa.user.security.JwtAuthenticationFilter;
import com.msa.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomAccessDeniedHandler accessDeniedHandler; // 403 핸들러 추가
    private final CustomAuthenticationEntryPoint authenticationEntryPoint; // 401 핸들러 추가
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtUtil jwtUtil;

    private final String[] PERMIT_URL_ARRAY_SWAGGER = {
            "/admin/replay/**", // 임시: 운영자 컨트롤러
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    private final String[] PERMIT_API_ARRAY_SWAGGER = {};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_API_ARRAY_SWAGGER)
                        .permitAll()
                        .requestMatchers(PERMIT_URL_ARRAY_SWAGGER)
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint) // 401 예외 핸들러 적용
                        .accessDeniedHandler(accessDeniedHandler) // 403 예외 핸들러 적용
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Filter between이 되지 않기에 jwtAuthenticationFilter를 기준으로 앞 뒤에 필터 세팅 진행
                // UsernamePasswordAuthenticationFilter가 실행되기 전에 jwtAuthenticationFilter를 실행시켜라
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class) // JWT 필터 적용

                // JwtAuthenticationFilter가 실행된 뒤에 TenantFilter를 실행시켜라
                // 필터 순서: JwtAuthenticationFilter
                //           -> TenantFilter
                //           -> UsernamePasswordAuthenticationFilter
                .addFilterAfter(tenantFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Security가 AuthenticationManager 빈을 필요로 해서, 기본으로 만들어진 AuthenticationManager를 그냥 가져다 리턴하는 코드
     **/
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        // JWT 기반이라 사실 AuthenticationManager 필요 없음
        return configuration.getAuthenticationManager();
    }

    @Bean
    public TenantFilter tenantFilter() {
        return new TenantFilter(jwtAuthenticationFilter, jwtUtil);
    }

    /**
     * TenantFilter는 @Bean으로도 등록되어있고, Spring Security 체인에도 직접 넣고 있음.
     * Filter 타입 빈은 Spring Boot가 서블릿 필터로도 자동 등록.
     * 따라서 하기 로직을 수행하여 TenantFilter는 Spring Security 체인 안에서만 실행 되도록 처리
     * 즉, 필터가 Spring Security 체인에 딱 한 번 등록되도록 설정하는 것임.
     */
    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(tenantFilter);

        // 필터를 서블릿 필터로 자동 등록하지 않도록 처리
        registration.setEnabled(false);
        return registration;
    }
}
