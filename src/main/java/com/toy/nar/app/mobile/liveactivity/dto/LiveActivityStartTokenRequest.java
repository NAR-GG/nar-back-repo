package com.toy.nar.app.mobile.liveactivity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * push-to-start 토큰 등록 요청.
 *
 * <p>카드 단위 토큰과 달리 앱 단위라 matchId 가 없다. 이 토큰이 있어야 서버가
 * 앱 실행 없이 잠금화면 카드를 만들 수 있다.</p>
 */
public record LiveActivityStartTokenRequest(
		@NotBlank(message = "pushToken은 필수입니다.")
		String pushToken) {
}
