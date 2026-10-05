package com.toy.nar.common.error.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.common.error.ErrorCode;

/**
 * 5xx 만 Discord 로 나가야 한다. 4xx 는 예상된 비즈니스 흐름이라 알림이면 노이즈다.
 */
class GlobalExceptionHandlerAlertTest {

	private final NotificationService notificationService = mock(NotificationService.class);
	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(notificationService);
	private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v3/teams");

	@Test
	@DisplayName("4xx CustomException 은 알리지 않는다")
	void 사용자_오류는_알리지_않는다() {
		handler.handleCustomException(new CustomException(ErrorCode.INVALID_INPUT_VALUE), request);

		verify(notificationService, never()).sendServerErrorNotification(any(), any(), any());
	}

	@Test
	@DisplayName("5xx CustomException 은 요청 경로와 함께 알린다")
	void 서버_오류는_알린다() {
		handler.handleCustomException(new CustomException(ErrorCode.INTERNAL_SERVER_ERROR), request);

		verify(notificationService).sendServerErrorNotification(eq("GET"), eq("/api/v3/teams"), any());
	}

	@Test
	@DisplayName("잡히지 않은 예외는 500 으로 내려가며 알린다")
	void 미처리_예외도_알린다() {
		handler.handleException(new IllegalStateException("boom"), request);

		verify(notificationService).sendServerErrorNotification(eq("GET"), eq("/api/v3/teams"), any());
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("에러 줄에 붙는 맥락은 메서드·경로·회원이며 쿼리스트링은 뺀다")
	void 로그_맥락() {
		MockHttpServletRequest withQuery = new MockHttpServletRequest("GET", "/api/v3/teams");
		withQuery.setQueryString("token=secret");
		assertThat(GlobalExceptionHandler.context(withQuery)).isEqualTo("[GET]/api/v3/teams uid=anon");

		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(12L, null, List.of()));
		assertThat(GlobalExceptionHandler.context(withQuery)).isEqualTo("[GET]/api/v3/teams uid=m:12");
	}
}
