package com.toy.nar.domain.member.entity;

/**
 * 알림 피드 종류. 값은 FCM 푸시의 {@code data.type} 과 일치해야 한다.
 *
 * <p>COMMUNITY_* 4종 중 발송이 붙은 것은 COMMENT/REPLY 다. REPORT_RESULT 와
 * RESTRICTION 은 처리 주체(백오피스 신고 큐·제재 발급)가 생길 때 발송이 붙는다 —
 * 타입을 미리 두는 이유는 앱 알림함 탭 필터가 타입 문자열로 갈리기 때문이다.</p>
 */
public enum MemberNotificationType {
	SET_START,
	SET_END,
	LIVE_EVENT,
	PLAYER_SOLO_RANK_STARTED,
	COMMUNITY_COMMENT,
	COMMUNITY_REPLY,
	COMMUNITY_REPORT_RESULT,
	COMMUNITY_RESTRICTION
}
