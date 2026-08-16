package com.toy.nar.api.mobile.subscription.dto;

import com.toy.nar.app.mobile.subscription.dto.MatchNotificationToggles;
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

		@Schema(description = "라이브 이벤트 알림 마스터 스위치. 생략 시 true", example = "true")
		Boolean liveEventEnabled,

		@Schema(description = "킬 알림. 생략 시 true. 세트당 약 30건으로 라이브 이벤트의 60%다", example = "true")
		Boolean killEnabled,

		@Schema(description = "바론 알림. 생략 시 true", example = "true")
		Boolean baronEnabled,

		@Schema(description = "드래곤 알림. 생략 시 true", example = "true")
		Boolean dragonEnabled,

		@Schema(description = "포탑 알림. 생략 시 true. 세트당 약 12건", example = "true")
		Boolean towerEnabled,

		@Schema(description = "억제기 알림. 생략 시 true", example = "true")
		Boolean inhibitorEnabled) {

	public MatchNotificationToggles toggles() {
		return new MatchNotificationToggles(
				setStartEnabled, setEndEnabled, liveEventEnabled,
				killEnabled, baronEnabled, dragonEnabled, towerEnabled, inhibitorEnabled);
	}

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
