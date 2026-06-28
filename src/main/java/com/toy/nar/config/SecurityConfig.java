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
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                                "/api/mobile/me/notifications/**"
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
