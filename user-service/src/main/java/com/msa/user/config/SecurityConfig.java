package com.msa.user.config;
import com.msa.tenant.config.TenantFilter;
import com.msa.user.security.CustomAccessDeniedHandler;
import com.msa.user.security.CustomAuthenticationEntryPoint;
import com.msa.user.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
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

    private final String[] PERMIT_URL_ARRAY_SWAGGER = {
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
        return new TenantFilter();
    }
}
