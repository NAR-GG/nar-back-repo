package com.toy.nar.domain.member.repository;

import java.util.Collection;
import java.util.List;

/**
 * 잠금화면 카드(push-to-start) 발행 이력. 회원·매치당 한 장을 보장한다.
 *
 * <p>JPA 엔티티를 두지 않고 JDBC 로만 다룬다 — 읽기 경로가 없고, 필요한 연산이
 * "한 번에 선점"과 "매치 단위 삭제" 둘뿐이라 영속 컨텍스트가 할 일이 없다.
 * {@code MemberTeamEventPushDeliveryRepositoryImpl} 과 같은 이유·같은 모양이다.</p>
 */
public interface LiveActivityCardDispatchRepository {

	/**
	 * 아직 카드를 발행하지 않은 회원만 선점하고, 그 회원 id 를 돌려준다.
	 *
	 * <p>이미 이 매치에 발행 이력이 있으면 제외된다. 즉 세트가 바뀌어도 다시 발행하지 않는다 —
	 * 카드는 매치당 한 장이고, 세트 전환은 갱신 토큰으로 반영한다.</p>
	 */
	List<Long> claimAll(Collection<Long> memberIds, String matchId, int setNumber);

	/**
	 * 이 회원의 이 매치 발행 이력이 {@code staleAfter} 보다 오래됐으면 지운다 — 다시 발행할 수 있게 만든다.
	 *
	 * <p>사용자가 카드를 지운 뒤 재구독하는 경로({@code startCardForMember})에서 부른다.
	 * 이걸 안 지우면 재구독해도 선점에 걸려 카드가 영구히 안 뜬다.</p>
	 *
	 * <p>나이 조건이 있는 이유 — 방금 발행한 것까지 풀면 짧은 간격의 연속 구독 액션이 카드를 두 장
	 * 만든다. 갱신 토큰은 발행 뒤 2~10초 있어야 올라오므로(실측 2026-08-16 매치 115548147900619033,
	 * 19:45:11 발송 → 19:45:13~21 등록) 그 창 안에서는 {@code closePreviousCard} 가 닫을 토큰을
	 * 찾지 못해 앞 카드가 살아남는다. 실제로 member 10 이 17:07:37 / 17:07:39 두 번 따라잡혀
	 * 2장이 됐다.</p>
	 */
	int release(Long memberId, String matchId, java.time.Duration staleAfter);

	/** 매치가 끝나 더 발행할 일이 없는 이력을 지운다. 테이블이 무한히 자라지 않게 하는 유일한 경로다. */
	int deleteAllByMatchId(String matchId);
}
