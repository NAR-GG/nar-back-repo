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
                                HttpMethod.PUT,
                                "/api/mobile/live/games/*/participants/*/my-rating"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/mobile/live/games/*/participants/*/my-rating"
                        ).authenticated()
                        .requestMatchers(
                                "/api/mobile/me/notification-subscriptions/**"
                        ).authenticated()
                        .requestMatchers(
                                "/api/auth/me", "/api/auth/logout", "/api/auth/onboarding"
                        ).authenticated()
                        .requestMatchers(
                                "/api/**",
                                "/oauth2/**", "/login/oauth2/**",
                                "/swagger-ui/**", "/v3/api-docs/**",
                                "/actuator/**"
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
