package com.toy.nar.app.mobile.subscription.dto;

import com.toy.nar.domain.member.entity.MemberMatchSubscription;

/**
 * 경기 구독 한 건의 알림 토글 상태. 앱이 경기별 알림 설정 화면을 그릴 때 쓴다.
 *
 * <p>목록 조회({@code GET /match-subscriptions})는 벨 아이콘용 matchId 배열이라 그대로 둔다 —
 * 앱이 그 응답 모양에 의존하고 있고, 설정 화면은 한 경기만 필요하다.</p>
 */
public record MatchSubscriptionResponse(
		String matchId,
		boolean subscribed,
		boolean setStartEnabled,
		boolean setEndEnabled,
		boolean liveEventEnabled,
		boolean killEnabled,
		boolean baronEnabled,
		boolean dragonEnabled,
		boolean towerEnabled,
		boolean inhibitorEnabled) {

	public static MatchSubscriptionResponse from(MemberMatchSubscription subscription) {
		return new MatchSubscriptionResponse(
				subscription.getMatchId(),
				true,
				subscription.isSetStartEnabled(),
				subscription.isSetEndEnabled(),
				subscription.isLiveEventEnabled(),
				subscription.isKillEnabled(),
				subscription.isBaronEnabled(),
				subscription.isDragonEnabled(),
				subscription.isTowerEnabled(),
				subscription.isInhibitorEnabled());
	}

	/** 구독 전 상태. 앱이 설정 시트를 미리 열어 볼 때의 기본값이다. */
	public static MatchSubscriptionResponse notSubscribed(String matchId) {
		return new MatchSubscriptionResponse(
				matchId, false, true, true, true, true, true, true, true, true);
	}
}
