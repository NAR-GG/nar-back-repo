package com.toy.nar.common.error.exception;

import com.toy.nar.common.error.ErrorCode;

import lombok.Getter;

/**
 * 커뮤니티 규칙이 시간 조건으로 요청을 막은 경우(응원팀 변경 쿨다운, 작성 간격 제한).
 * 남은 시간을 표준 {@code Retry-After} 헤더로 내려 앱이 문구 하나로 여러 케이스를 그린다.
 */
@Getter
public class CommunityWriteBlockedException extends CustomException {

	private final long retryAfterSeconds;

	public CommunityWriteBlockedException(ErrorCode errorCode, long retryAfterSeconds) {
		super(errorCode);
		this.retryAfterSeconds = retryAfterSeconds;
	}
}
