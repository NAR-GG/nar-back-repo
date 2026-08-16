package com.toy.nar.api.mobile.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선수 알림 토글 변경 요청. 보내지 않은 값은 기존 값을 유지한다")
public record PlayerSubscriptionToggleRequest(
		@Schema(description = "솔랭 시작 알림", example = "true")
		Boolean startEnabled,

		@Schema(description = "솔랭 종료 알림(승패·KDA)", example = "false")
		Boolean endEnabled) {
}
