package com.toy.nar.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	/* 400 BAD_REQUEST : 잘못된 요청 */
	INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	INVALID_COMBINATION_ID(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 조합 ID입니다."),

	/* 404 NOT_FOUND : 리소스를 찾을 수 없음 */
	COMBINATION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 조건의 조합 데이터를 찾을 수 없습니다."),
	DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "데이터가 존재하지 않습니다."),

	/* 500 INTERNAL_SERVER_ERROR : 서버 내부 오류 */
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

	private final HttpStatus httpStatus;
	private final String message;
}
