package com.toy.nar.app.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(SECRET);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private Authentication runFilterWithToken(String token) throws Exception {
        var filter = new JwtAuthenticationFilter(tokenProvider);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void role_ADMIN_토큰이면_ROLE_ADMIN_부여() throws Exception {
        Authentication auth = runFilterWithToken(tokenProvider.createAccessToken(7L, true, "ADMIN"));
        assertThat(hasRole(auth, "ROLE_ADMIN")).isTrue();
        assertThat(hasRole(auth, "ROLE_USER")).isTrue();
    }

    @Test
    void role_USER_토큰이면_ROLE_USER만() throws Exception {
        Authentication auth = runFilterWithToken(tokenProvider.createAccessToken(99L, true, "USER"));
        assertThat(hasRole(auth, "ROLE_ADMIN")).isFalse();
        assertThat(hasRole(auth, "ROLE_USER")).isTrue();
    }

    @Test
    void role_claim_없는_구버전토큰이면_USER로_간주() throws Exception {
        // createAccessToken 의 3-인자 시그니처 이전(=role 미포함) 토큰 호환성.
        String legacy = io.jsonwebtoken.Jwts.builder()
                .subject("1")
                .claim("onboarded", true)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
        Authentication auth = runFilterWithToken(legacy);
        assertThat(hasRole(auth, "ROLE_ADMIN")).isFalse();
        assertThat(hasRole(auth, "ROLE_USER")).isTrue();
    }
}
