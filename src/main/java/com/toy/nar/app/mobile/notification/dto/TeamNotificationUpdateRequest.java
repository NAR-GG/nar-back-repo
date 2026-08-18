package com.toy.nar.app.mobile.notification.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 팀 알림 토글 변경 요청.
 *
 * <p>기존 3종은 필수다(구버전 앱도 항상 보낸다). 라이브 이벤트 종류별 토글은 nullable —
 * 구버전 앱이 안 보내는데 false 로 해석하면 받던 알림이 조용히 끊긴다.</p>
 */
public record TeamNotificationUpdateRequest(
		@NotNull Boolean setStartEnabled,
		@NotNull Boolean setEndEnabled,
		@NotNull Boolean liveEventEnabled,
		Boolean killEnabled,
		Boolean baronEnabled,
		Boolean dragonEnabled,
		Boolean towerEnabled,
		Boolean inhibitorEnabled) {
}
