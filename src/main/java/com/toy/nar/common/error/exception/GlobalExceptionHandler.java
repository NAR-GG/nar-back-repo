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

	@ExceptionHandler(CustomException.class)
	protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
		log.error("handleCustomException throw CustomException : {}", e.getErrorCode());
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
		log.error("handleEntityNotFoundException : {}", e.getMessage());
		return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
	}
}
