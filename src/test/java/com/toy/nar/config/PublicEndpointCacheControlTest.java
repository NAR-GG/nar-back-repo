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
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * 공개 엔드포인트의 캐시 헤더 규약 검증.
 *
 * <p>Spring Security 는 기본으로 모든 응답에
 * {@code Cache-Control: no-cache, no-store, max-age=0, must-revalidate} 를 붙인다.
 * 그래서 모바일 앱이 리그·팀 목록 같은 사실상 불변 데이터도 매번 다시 받아왔다.
 *
 * <p>{@code CacheControlHeadersWriter} 는 Cache-Control/Pragma/Expires 중 하나라도 이미
 * 있으면 아무것도 쓰지 않으므로, 컨트롤러에서 {@code .cacheControl(...)} 만 붙이면
 * SecurityConfig 를 건드리지 않고 해당 엔드포인트만 캐시를 허용할 수 있다.
 * 이 테스트는 그 동작(스프링 시큐리티 버전이 올라가도 덮어쓰지 않는다)을 고정한다.
 *
 * <p>JPA/DB 없이 시큐리티 필터 체인만 띄운다({@link ApiAuthenticationEntryPointTest} 와 동일한 이유).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityConfig.class, PublicEndpointCacheControlTest.Mocks.class})
class PublicEndpointCacheControlTest {

	@Configuration
	@EnableWebMvc
	static class Mocks {

		/**
		 * 실제 공개 컨트롤러와 같은 방식으로 캐시 헤더를 붙이는 대역.
		 *
		 * <p>@Configuration 의 멤버 클래스이고 @RestController 가 @Component 이므로 스프링이
		 * 자동 등록한다. @Bean 메서드로 또 등록하면 Ambiguous mapping 으로 컨텍스트가 죽는다.
		 */
		@RestController
		static class StubController {

			@GetMapping("/api/test/cached")
			ResponseEntity<String> cached() {
				return ResponseEntity.ok()
						.cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
						.body("ok");
			}

			@GetMapping("/api/test/uncached")
			ResponseEntity<String> uncached() {
				return ResponseEntity.ok("ok");
			}
		}

		@Bean
		JwtTokenProvider jwtTokenProvider() {
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
	@DisplayName("컨트롤러가 max-age 를 주면 시큐리티가 no-store 로 덮어쓰지 않는다")
	void controllerCacheControlSurvivesSecurityFilterChain() throws Exception {
		mockMvc.perform(get("/api/test/cached"))
				.andExpect(header().string("Cache-Control", "max-age=3600"))
				.andExpect(header().doesNotExist("Pragma"))
				.andExpect(header().doesNotExist("Expires"));
	}

	@Test
	@DisplayName("캐시 헤더를 지정하지 않은 응답은 기존 no-store 를 유지한다(인증 경로 보호)")
	void endpointsWithoutExplicitCacheControlStayNoStore() throws Exception {
		mockMvc.perform(get("/api/test/uncached"))
				.andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
	}
}
