package com.toy.nar.common.error.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.ErrorResponse;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private final NotificationService notificationService;

	@ExceptionHandler(CommunityWriteBlockedException.class)
	protected ResponseEntity<ErrorResponse> handleCommunityWriteBlocked(CommunityWriteBlockedException e) {
		log.warn("handleCommunityWriteBlocked : {} retryAfter={}s", e.getErrorCode(), e.getRetryAfterSeconds());
		return ResponseEntity.status(e.getErrorCode().getHttpStatus())
				.header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
				.body(ErrorResponse.toResponseEntity(e.getErrorCode()).getBody());
	}

	@ExceptionHandler(CustomException.class)
	protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e, HttpServletRequest request) {
		// 4xx는 예상된 비즈니스 흐름이라 warn. 5xx만 ERROR 로그 + Discord 알림 대상이다.
		if (e.getErrorCode().getHttpStatus().is5xxServerError()) {
			log.error("handleCustomException throw CustomException : {}", e.getErrorCode(), e);
			notificationService.sendServerErrorNotification(request.getMethod(), request.getRequestURI(), e);
		} else {
			log.warn("handleCustomException throw CustomException : {}", e.getErrorCode());
		}
		return ErrorResponse.toResponseEntity(e.getErrorCode());
	}

	// 혹시 모른 IllegalArgumentException 처리 (400)
	@ExceptionHandler(IllegalArgumentException.class)
	protected ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
		log.warn("handleIllegalArgumentException: {}", e.getMessage());
		return ErrorResponse.toResponseEntity(ErrorCode.INVALID_INPUT_VALUE);
	}

	// 그 외 모든 예외 처리 (500)
	//
	// ponytail: 클라이언트 연결 끊김(broken pipe)류가 여기로 흘러오면 알림이 섞인다.
	//           NotificationService 의 5분 스로틀이 폭주는 막으니, 실제로 시끄러워지면 그때 예외 타입을 걸러낸다.
	@ExceptionHandler(Exception.class)
	protected ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
		log.error("handleException : {}", e.getMessage(), e);
		notificationService.sendServerErrorNotification(request.getMethod(), request.getRequestURI(), e);
		return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
	}
}
