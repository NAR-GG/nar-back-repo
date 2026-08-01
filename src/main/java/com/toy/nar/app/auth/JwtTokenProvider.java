package com.toy.nar.app.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final long ACCESS_TOKEN_EXPIRY_MS = 30 * 60 * 1000L;          // 30분
    private static final long REFRESH_TOKEN_EXPIRY_MS = 14 * 24 * 60 * 60 * 1000L; // 14일

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long memberId, boolean isOnboarded, String role) {
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("onboarded", isOnboarded)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_MS))
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(Long memberId) {
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                // jti 가 없으면 페이로드가 (subject, iat, exp)뿐인데 iat/exp 는 초 단위라
                // 같은 회원에게 같은 초에 발급한 토큰이 바이트 동일해진다. DB unique(token)에
                // 걸려 동시 리프레시/로그인이 500 으로 죽었다(실측 2026-07-31 새벽 14건).
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY_MS))
                .signWith(secretKey)
                .compact();
    }

    public LocalDateTime getRefreshTokenExpiry() {
        return LocalDateTime.now().plusDays(14);
    }

    public Long getMemberId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /** 토큰의 role claim. 구버전 토큰(claim 없음)은 USER 로 간주. */
    public String getRole(String token) {
        String role = parseClaims(token).get("role", String.class);
        return role != null ? role : "USER";
    }

    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
