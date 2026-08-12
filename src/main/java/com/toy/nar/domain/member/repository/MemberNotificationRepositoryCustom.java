package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberNotificationType;

import java.util.Collection;
import java.util.Map;

public interface MemberNotificationRepositoryCustom {

	/**
	 * 같은 알림 1건을 여러 회원에게 한 번에 적재한다.
	 *
	 * <p>{@code saveAll} 로는 왕복이 회원 수만큼 난다 — {@code MemberNotification} 의 id 가
	 * {@code GenerationType.IDENTITY} 라서 Hibernate 가 INSERT 배치를 아예 비활성화한다
	 * ({@code hibernate.jdbc.batch_size} 와 무관하다). 프로덕션은 앱(EC2 서울)과 DB(OCI 춘천)가
	 * 분리돼 있어 왕복당 10ms 대이고, 실측 2026-08-11 Zeus 구독 1,440명 적재가 약 20초였다.</p>
	 *
	 * <p>그래서 다중 VALUES 로 직접 넣는다({@code PlayerSoloRankPushDeliveryRepositoryImpl}
	 * 과 같은 방식). 팬아웃이 푸시 발송 전에 피드를 남기므로, 이 적재 시간이 곧 알림 지연이 된다.</p>
	 *
	 * @param data JSON 컬럼에 그대로 보존할 푸시 payload. null 이면 컬럼도 null.
	 * @return 적재한 행 수
	 */
	int insertAll(
			Collection<Long> memberIds,
			MemberNotificationType type,
			String title,
			String body,
			Map<String, String> data);
}
