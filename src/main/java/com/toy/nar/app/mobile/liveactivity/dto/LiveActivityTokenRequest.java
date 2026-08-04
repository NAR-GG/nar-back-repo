package com.toy.nar.app.mobile.liveactivity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * iOS 앱이 Live Activity 를 시작하며 받은 ActivityKit 푸시 토큰 등록 요청.
 *
 * <p>토큰은 액티비티 단위라 카드를 띄울 때마다 새로 발급되고, 수명 중에 갱신될 수도 있다
 * ({@code Activity.pushTokenUpdates}). 받을 때마다 그대로 올리면 된다.</p>
 */
public record LiveActivityTokenRequest(
		@NotBlank(message = "matchId는 필수입니다.")
		String matchId,

		@NotBlank(message = "pushToken은 필수입니다.")
		String pushToken) {
}
