package com.toy.nar.domain.member.repository;

import java.util.Collection;
import java.util.List;

/**
 * 솔랭 푸시 팬아웃용 벌크 연산.
 *
 * <p>구독자 단위 쿼리를 돌면 왕복이 구독자 수에 비례한다. 앱(AWS 서울)→DB(Oracle Cloud 춘천)
 * 왕복이 6.5ms 라 1,500명이면 그것만으로 분 단위가 되고, 팬아웃이 솔랭 폴 스레드에서
 * 돌기 때문에 그 시간 동안 새 게임 감지가 멈춘다. 팀 라이브 이벤트 경로와 같은 처방이다.</p>
 */
public interface PlayerSoloRankPushDeliveryRepositoryCustom {

	/**
	 * 구독자 전원을 한 번에 예약하고 실제 발송 대상만 돌려준다.
	 *
	 * <p>판정 규칙은 신규이거나 FAILED 이거나 PENDING 으로 5분 넘게 방치된 건만 통과이고,
	 * SENT 는 제외된다(게임당 1회 발송).</p>
	 */
	List<Long> reserveAll(Collection<Long> memberIds, Long playerId, String gameId);

	int markSentAll(Collection<Long> memberIds, Long playerId, String gameId);

	int markFailedAll(Collection<Long> memberIds, Long playerId, String gameId, String errorMessage);
}
