package com.toy.nar.config;

import com.toy.nar.app.auth.CookieOAuth2AuthorizationRequestRepository;
import com.toy.nar.app.auth.CustomOAuth2UserService;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.OAuth2AuthenticationFailureHandler;
import com.toy.nar.app.auth.OAuth2AuthenticationSuccessHandler;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 실패 응답 규약 검증.
 *
 * <p>/api/** 가 302 로그인 리다이렉트를 주면 모바일 클라이언트는 리다이렉트를 따라가
 * 로그인 HTML 을 200 으로 받는다. 그러면 토큰 만료가 "빈 응답"으로 보여 화면이 조용히 비고
 * 401 기반 토큰 리프레시도 트리거되지 않는다(마이구독 알림 목록 미갱신 원인). 반드시 401 이어야 한다.
 *
 * <p>JPA/DB 없이 시큐리티 필터 체인만 띄운다. {@code @WebMvcTest} 는 메인 클래스의
 * {@code @EnableJpaRepositories} 때문에 EntityManagerFactory 를 요구해서 쓰지 않는다.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityConfig.class, ApiAuthenticationEntryPointTest.Mocks.class})
class ApiAuthenticationEntryPointTest {

	@Configuration
	@EnableWebMvc // requestMatchers(String...) 가 MvcRequestMatcher 를 쓰므로 HandlerMappingIntrospector 필요
	static class Mocks {

		@Bean
		JwtTokenProvider jwtTokenProvider() {
			// 어떤 토큰도 유효하지 않다고 본다(만료·위조 토큰 상황).
			return mock(JwtTokenProvider.class);
		}

		@Bean
		CustomOAuth2UserService customOAuth2UserService() {
			return mock(CustomOAuth2UserService.class);
		}

		@Bean
		OAuth2AuthenticationSuccessHandler successHandler() {
			return mock(OAuth2AuthenticationSuccessHandler.class);
		}

		@Bean
		OAuth2AuthenticationFailureHandler failureHandler() {
			return mock(OAuth2AuthenticationFailureHandler.class);
		}

		@Bean
		CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository() {
			return mock(CookieOAuth2AuthorizationRequestRepository.class);
		}

		@Bean
		ClientRegistrationRepository clientRegistrationRepository() {
			return mock(ClientRegistrationRepository.class);
		}
	}

	private MockMvc mockMvc;

	@BeforeEach
	void setUp(WebApplicationContext context) {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean("springSecurityFilterChain", Filter.class))
				.build();
	}

	@Test
	@DisplayName("토큰 없이 API 를 호출하면 302 리다이렉트가 아니라 401 이다")
	void apiWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/mobile/me/notifications"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("만료·위조 토큰도 401 이다")
	void apiWithInvalidTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/mobile/me/notifications")
						.header("Authorization", "Bearer expired.or.forged"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("브라우저 웹 경로는 기존 로그인 리다이렉트(302)를 유지한다")
	void nonApiPathStillRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/some-web-page"))
				.andExpect(status().is3xxRedirection());
	}
}
