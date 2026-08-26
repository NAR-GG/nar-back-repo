package com.toy.nar.common.error.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(CommunityWriteBlockedException.class)
	protected ResponseEntity<ErrorResponse> handleCommunityWriteBlocked(CommunityWriteBlockedException e) {
		log.warn("handleCommunityWriteBlocked : {} retryAfter={}s", e.getErrorCode(), e.getRetryAfterSeconds());
		return ResponseEntity.status(e.getErrorCode().getHttpStatus())
				.header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
				.body(ErrorResponse.toResponseEntity(e.getErrorCode()).getBody());
	}

	@ExceptionHandler(CustomException.class)
	protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
		// 4xx는 예상된 비즈니스 흐름이라 warn — ERROR 로그는 Sentry 이벤트로 수집되므로 5xx만 남긴다
		if (e.getErrorCode().getHttpStatus().is5xxServerError()) {
			log.error("handleCustomException throw CustomException : {}", e.getErrorCode(), e);
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
	@ExceptionHandler(Exception.class)
	protected ResponseEntity<ErrorResponse> handleException(Exception e) {
		log.error("handleException : {}", e.getMessage(), e);
		return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
	}
}
