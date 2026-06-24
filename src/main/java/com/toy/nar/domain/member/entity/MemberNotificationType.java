package com.toy.nar.domain.member.entity;

/**
 * 마이구독 알림 피드 종류. 값은 FCM 푸시의 {@code data.type} 과 일치해야 한다.
 */
public enum MemberNotificationType {
	SET_START,
	SET_END,
	LIVE_EVENT,
	PLAYER_SOLO_RANK_STARTED
}
