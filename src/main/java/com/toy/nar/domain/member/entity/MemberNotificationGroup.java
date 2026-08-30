package com.toy.nar.domain.member.entity;

import java.util.Set;

/**
 * 알림 타입 묶음. 앱 알림함이 커뮤니티 전용이 되면서(단일 게시판 전환) 목록·미읽음·
 * 모두읽음을 타입 하나가 아니라 묶음 단위로 걸러야 해서 생겼다. 새 타입이 늘 때
 * 앱 배포 없이 서버 묶음만 고치면 되도록, 앱은 타입 나열이 아니라 그룹 이름을 보낸다.
 */
public enum MemberNotificationGroup {

	COMMUNITY(Set.of(
			MemberNotificationType.COMMUNITY_COMMENT,
			MemberNotificationType.COMMUNITY_REPLY,
			MemberNotificationType.COMMUNITY_LIKE,
			MemberNotificationType.COMMUNITY_REPORT_RESULT,
			MemberNotificationType.COMMUNITY_RESTRICTION));

	private final Set<MemberNotificationType> types;

	MemberNotificationGroup(Set<MemberNotificationType> types) {
		this.types = types;
	}

	public Set<MemberNotificationType> types() {
		return types;
	}
}
