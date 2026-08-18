package com.toy.nar.api.mobile.subscription.dto;

import com.toy.nar.app.mobile.subscription.dto.MatchNotificationToggles;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 경기 구독 알림 토글 변경 요청. 경기 ID 는 경로에 있으므로 body 에 넣지 않는다.
 *
 * <p>모든 값이 nullable 이고 보내지 않은 필드는 기존 값을 유지한다. 구버전 앱이 모르는
 * 필드를 안 보내는데 false 로 해석하면 받던 알림이 조용히 끊긴다.</p>
 */
@Schema(description = "경기 구독 알림 토글 변경 요청. 보내지 않은 필드는 기존 값을 유지한다")
public record MatchSubscriptionToggleRequest(
		@Schema(description = "세트 시작 알림", example = "true")
		Boolean setStartEnabled,

		@Schema(description = "세트 종료 알림", example = "true")
		Boolean setEndEnabled,

		@Schema(description = "라이브 이벤트 마스터 스위치. 끄면 아래 5종이 켜져 있어도 안 온다", example = "true")
		Boolean liveEventEnabled,

		@Schema(description = "킬 알림. 세트당 약 30건으로 라이브 이벤트의 60%다", example = "false")
		Boolean killEnabled,

		@Schema(description = "바론 알림", example = "true")
		Boolean baronEnabled,

		@Schema(description = "드래곤 알림", example = "true")
		Boolean dragonEnabled,

		@Schema(description = "포탑 알림. 세트당 약 12건", example = "false")
		Boolean towerEnabled,

		@Schema(description = "억제기 알림", example = "true")
		Boolean inhibitorEnabled) {

	public MatchNotificationToggles toggles() {
		return new MatchNotificationToggles(
				setStartEnabled, setEndEnabled, liveEventEnabled,
				killEnabled, baronEnabled, dragonEnabled, towerEnabled, inhibitorEnabled);
	}
}
