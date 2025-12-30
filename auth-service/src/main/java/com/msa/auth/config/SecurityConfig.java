package com.msa.auth.config;
import com.msa.auth.security.CustomAccessDeniedHandler;
import com.msa.auth.security.CustomAuthenticationEntryPoint;
import com.msa.auth.security.JwtAuthenticationFilter;
import com.msa.auth.security.CustomUserDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomUserDetailService userDetailsService;
    private final CustomAccessDeniedHandler accessDeniedHandler; // 403 핸들러 추가
    private final CustomAuthenticationEntryPoint authenticationEntryPoint; // 401 핸들러 추가
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final String[] PERMIT_URL_ARRAY_SWAGGER = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    private final String[] PERMIT_API_ARRAY_SWAGGER = {
            "/auth/signUp",
            "/auth/login"
    };

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
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class); // JWT 필터 적용
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
