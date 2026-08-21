package com.msa.auth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.exp}")
    private long EXPIRATION_TIME;

    public String generateToken(Long userNo, String userId, String role, String tenantKey) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("userNo", userNo)
                .claim("tenantKey", tenantKey)
                .claim("role", role)
                .setIssuedAt(new Date()) // 발급 시간
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // 만료 시간
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUserId(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8))).build().parseClaimsJws(token);
            return true; // 검증 성공
        } catch (ExpiredJwtException e) {
            log.warn("JWT 토큰이 만료되었습니다.");
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 형식입니다.");
        } catch (MalformedJwtException e) {
            log.warn("JWT 토큰이 올바르게 구성되지 않았습니다.");
        } catch (SignatureException e) {
            log.warn("JWT 서명이 올바르지 않습니다.");
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어 있습니다.");
        }
        return false; // 검증 실패
    }
}
