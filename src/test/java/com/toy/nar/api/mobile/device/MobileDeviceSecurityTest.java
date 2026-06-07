package com.toy.nar.api.mobile.device;

import com.toy.nar.app.auth.CookieOAuth2AuthorizationRequestRepository;
import com.toy.nar.app.auth.CustomOAuth2UserService;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.OAuth2AuthenticationFailureHandler;
import com.toy.nar.app.auth.OAuth2AuthenticationSuccessHandler;
import com.toy.nar.app.mobile.device.MobileDeviceService;
import com.toy.nar.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(MobileDeviceController.class)
@ContextConfiguration(classes = {
		MobileDeviceController.class,
		SecurityConfig.class
})
@TestPropertySource(properties = {
		"spring.security.oauth2.client.registration.google.client-id=test-client",
		"spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
class MobileDeviceSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MobileDeviceService deviceService;

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
	void rejectsUnauthenticatedDeviceRegistration() throws Exception {
		int status = mockMvc.perform(post("/api/mobile/me/devices")
						.contentType("application/json")
						.content("{\"fcmToken\":\"token\",\"platform\":\"ANDROID\"}"))
				.andReturn()
				.getResponse()
				.getStatus();

		assertThat(status).isGreaterThanOrEqualTo(300);
	}
}
