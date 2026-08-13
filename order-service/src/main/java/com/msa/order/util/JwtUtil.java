package com.msa.order.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secretKey;

    public String getUserId(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String getRole(String token) {
        Claims claims = getClaims(token);
        return claims.get("role", String.class);
    }

    public String getTenantKey(String token) {
        Claims claims = getClaims(token);
        return claims.get("tenantKey", String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8))).build().parseClaimsJws(token);
            return true; // 검증 성공
        } catch (ExpiredJwtException e) {
            log.warn("JWT 토큰이 만료되었습니다. token={}", token);
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 형식입니다. token={}", token);
        } catch (MalformedJwtException e) {
            log.warn("JWT 토큰이 올바르게 구성되지 않았습니다. token={}", token);
        } catch (SignatureException e) {
            log.warn("JWT 서명이 올바르지 않습니다. token={}", token);
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어 있습니다.");
        }
        return false; // 검증 실패
    }
}
