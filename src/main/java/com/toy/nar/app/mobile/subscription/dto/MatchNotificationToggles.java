package com.toy.nar.app.mobile.subscription.dto;

/**
 * 경기 구독 알림 토글 묶음. 값이 {@code null} 이면 "안 보냈다"는 뜻이라 기존 값을 유지한다.
 *
 * <p>구버전 앱은 종류별 토글을 모른다. 안 보낸 필드를 false 로 해석하면 받던 알림이
 * 조용히 끊기므로 null 과 false 를 반드시 구분한다.</p>
 */
public record MatchNotificationToggles(
		Boolean setStartEnabled,
		Boolean setEndEnabled,
		Boolean liveEventEnabled,
		Boolean killEnabled,
		Boolean baronEnabled,
		Boolean dragonEnabled,
		Boolean towerEnabled,
		Boolean inhibitorEnabled) {

	public boolean setStartOrTrue() {
		return setStartEnabled == null || setStartEnabled;
	}

	public boolean setEndOrTrue() {
		return setEndEnabled == null || setEndEnabled;
	}

	public boolean liveEventOrTrue() {
		return liveEventEnabled == null || liveEventEnabled;
	}
}
