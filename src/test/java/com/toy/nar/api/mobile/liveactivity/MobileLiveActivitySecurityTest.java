package com.toy.nar.api.mobile.liveactivity;

import com.toy.nar.app.auth.CookieOAuth2AuthorizationRequestRepository;
import com.toy.nar.app.auth.CustomOAuth2UserService;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.OAuth2AuthenticationFailureHandler;
import com.toy.nar.app.auth.OAuth2AuthenticationSuccessHandler;
import com.toy.nar.app.mobile.liveactivity.LiveActivityTokenService;
import com.toy.nar.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(MobileLiveActivityController.class)
@ContextConfiguration(classes = {
		MobileLiveActivityController.class,
		SecurityConfig.class
})
@TestPropertySource(properties = {
		"spring.security.oauth2.client.registration.google.client-id=test-client",
		"spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
class MobileLiveActivitySecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LiveActivityTokenService tokenService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private CustomOAuth2UserService customOAuth2UserService;

	@MockitoBean
	private OAuth2AuthenticationSuccessHandler successHandler;

	@MockitoBean
	private OAuth2AuthenticationFailureHandler failureHandler;

	@MockitoBean
	private CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

	/**
	 * 401 자체보다 "서비스에 들어가지 않는 것"이 핵심이다. 서비스로 들어가면
	 * {@code @Transactional} 이 커넥션부터 잡고 나서 401 을 던진다 — 쿼리 없이 풀만 먹는다.
	 */
	@Test
	void rejectsUnauthenticatedStartTokenBeforeReachingService() throws Exception {
		int status = mockMvc.perform(post("/api/mobile/me/live-activities/start-token")
						.contentType("application/json")
						.content("{\"pushToken\":\"token\"}"))
				.andReturn()
				.getResponse()
				.getStatus();

		assertThat(status).isEqualTo(401);
		verifyNoInteractions(tokenService);
	}

	@Test
	void rejectsUnauthenticatedTokenRegistrationBeforeReachingService() throws Exception {
		int status = mockMvc.perform(post("/api/mobile/me/live-activities")
						.contentType("application/json")
						.content("{\"pushToken\":\"token\",\"matchId\":\"match-1\"}"))
				.andReturn()
				.getResponse()
				.getStatus();

		assertThat(status).isEqualTo(401);
		verifyNoInteractions(tokenService);
	}
}
