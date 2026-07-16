package com.toy.nar.api.mobile.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "경기 예약 알림 구독 요청")
public record MatchSubscribeRequest(
		@NotBlank
		@Schema(description = "구독할 경기 ID", example = "113990000000000001")
		String matchId,

		@Schema(description = "세트 시작 알림. 생략 시 true", example = "true")
		Boolean setStartEnabled,

		@Schema(description = "세트 종료 알림. 생략 시 true", example = "true")
		Boolean setEndEnabled,

		@Schema(description = "라이브 이벤트 알림. 생략 시 true", example = "true")
		Boolean liveEventEnabled) {

	// 구버전 앱(플래그 미전송) 호환: null 이면 기존처럼 3종 전부 ON.
	public boolean setStartEnabledOrDefault() {
		return setStartEnabled == null || setStartEnabled;
	}

	public boolean setEndEnabledOrDefault() {
		return setEndEnabled == null || setEndEnabled;
	}

	public boolean liveEventEnabledOrDefault() {
		return liveEventEnabled == null || liveEventEnabled;
	}
}
