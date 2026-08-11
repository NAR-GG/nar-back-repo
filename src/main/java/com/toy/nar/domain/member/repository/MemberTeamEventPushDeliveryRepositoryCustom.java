package com.toy.nar.domain.member.repository;

import java.util.Collection;
import java.util.List;

/**
 * 라이브 푸시 fan-out 용 배치 연산.
 *
 * <p>구독자마다 reserve/markSent 를 한 건씩 돌면 구독자 수만큼 DB 왕복이 난다. 앱(AWS 서울)과
 * DB(Oracle Cloud 춘천) 사이 왕복이 실측 6.5ms 라 구독자 747명 SET_START 발송이 37초 걸렸다
 * (2026-07-30 LCK HLE vs DK). 구독자 단위 루프를 벌크 문장으로 접어 왕복을 상수로 만든다.</p>
 */
public interface MemberTeamEventPushDeliveryRepositoryCustom {

	/**
	 * 후보 구독자를 한 번에 예약하고, 실제로 발송해야 하는 구독자만 돌려준다.
	 *
	 * <p>단건 {@code reserve} 와 판정 규칙이 같다 — 신규이거나, FAILED 이거나,
	 * PENDING 으로 1분 넘게 방치된 건만 통과한다. 이미 SENT 면 제외된다.</p>
	 */
	List<Long> reserveAll(
			Collection<Long> memberIds,
			String matchId,
			int setNumber,
			String eventType,
			long eventOrder);

	/** 발송 성공 구독자를 한 번에 SENT 로 마감한다. */
	int markSentAll(
			Collection<Long> memberIds,
			String matchId,
			int setNumber,
			String eventType,
			long eventOrder);

	/**
	 * 알림 잠자기라 푸시를 보내지 않고 알림함에만 남긴 구독자를 한 번에 마감한다.
	 *
	 * <p>재예약 대상(FAILED·stale PENDING)이 아니어야 잠자기가 끝난 뒤 뒤늦은 푸시가 안 나간다.</p>
	 */
	int markSkippedQuietAll(
			Collection<Long> memberIds,
			String matchId,
			int setNumber,
			String eventType,
			long eventOrder);

	/** 발송 실패 구독자를 한 번에 FAILED 로 마감한다. */
	int markFailedAll(
			Collection<Long> memberIds,
			String matchId,
			int setNumber,
			String eventType,
			long eventOrder,
			String errorMessage);
}
