package com.toy.nar.api.mobile.subscription;

import com.toy.nar.app.auth.CookieOAuth2AuthorizationRequestRepository;
import com.toy.nar.app.auth.CustomOAuth2UserService;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.OAuth2AuthenticationFailureHandler;
import com.toy.nar.app.auth.OAuth2AuthenticationSuccessHandler;
import com.toy.nar.app.mobile.subscription.MobilePlayerSubscriptionService;
import com.toy.nar.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(MobilePlayerSubscriptionController.class)
@ContextConfiguration(classes = {
		MobilePlayerSubscriptionController.class,
		SecurityConfig.class
})
@TestPropertySource(properties = {
		"spring.security.oauth2.client.registration.google.client-id=test-client",
		"spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
class MobilePlayerSubscriptionSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MobilePlayerSubscriptionService subscriptionService;

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

	@Test
	void rejectsUnauthenticatedRequest() throws Exception {
		int status = mockMvc.perform(get("/api/mobile/me/player-subscriptions"))
				.andReturn()
				.getResponse()
				.getStatus();

		assertThat(status).isGreaterThanOrEqualTo(300);
	}
}
