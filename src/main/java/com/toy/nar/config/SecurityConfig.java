package com.toy.nar.config;

import com.toy.nar.app.auth.CookieOAuth2AuthorizationRequestRepository;
import com.toy.nar.app.auth.CustomOAuth2UserService;
import com.toy.nar.app.auth.JwtAuthenticationFilter;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.OAuth2AuthenticationFailureHandler;
import com.toy.nar.app.auth.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/mobile/me/player-subscriptions/**",
                                "/api/mobile/me/devices/**",
                                "/api/mobile/me/ratings"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/mobile/live/games/*/participants/*/my-rating"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/mobile/live/games/*/participants/*/my-rating"
                        ).authenticated()
                        .requestMatchers(
                                "/api/mobile/me/notification-subscriptions/**",
                                "/api/mobile/me/notifications/**",
                                "/api/mobile/me/match-subscriptions/**",
                                // 여기 없으면 아래 /api/** permitAll 에 걸려 서비스까지 들어간다.
                                // 그러면 @Transactional 이 커넥션을 먼저 잡은 뒤에야 memberId null 을
                                // 발견해 401 을 던진다 — 쿼리 한 줄 없이 풀만 점유한다. 실측
                                // 2026-08-15 17:12 HLE vs KT 시작 때 start-token 110건이 401 이었고,
                                // 그 중 71건이 SQL 0건 상태로 커넥션을 중앙값 849ms 씩 기다렸다.
                                "/api/mobile/me/live-activities/**",
                                "/api/mobile/me/quiet-hours"
                        ).authenticated()
                        .requestMatchers(
                                "/api/auth/me", "/api/auth/logout", "/api/auth/onboarding"
                        ).authenticated()
                        // 백오피스 admin 영역. /api/** permitAll 보다 먼저 와야 적용된다(순서 우선).
                        .requestMatchers("/api/admin/**", "/api/debug/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/**",
                                "/oauth2/**", "/login/oauth2/**",
                                "/swagger-ui/**", "/v3/api-docs/**",
                                "/actuator/**",
                                // 정적 리소스(선수/팀 이미지 등)는 인증 없이 서빙. 없으면 302 로그인
                                // 리다이렉트가 떠서 <img>/Image.network 로딩이 실패한다.
                                "/images/**", "/static/**", "/favicon.ico"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // API 는 인증 실패 시 401 을 준다. 기본 진입점(oauth2Login 이 등록하는
                // LoginUrlAuthenticationEntryPoint)은 /login 으로 302 리다이렉트를 보내는데,
                // 모바일 클라이언트는 리다이렉트를 따라가 로그인 HTML 을 200 으로 받는다.
                // 그러면 토큰 만료가 "빈 응답"으로 보여 화면이 조용히 비고, 401 기반 토큰
                // 리프레시도 트리거되지 않는다(마이구독 알림 목록 미갱신 원인).
                // 브라우저 OAuth 로그인 흐름(/oauth2/**, /login/**)은 기존 302 를 유지해야 하므로
                // /api/** 에만 401 진입점을 적용한다.
                // 두 번째 매핑(AnyRequest → /login)은 생략하면 안 된다. 매핑이 하나뿐이면
                // 그 진입점이 전역 기본값이 되어 웹 경로까지 401 이 나간다.
                .exceptionHandling(exception -> exception
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                AnyRequestMatcher.INSTANCE))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(a -> a
                                .authorizationRequestRepository(authorizationRequestRepository))
                        .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
