package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.LiveActivityStartToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface LiveActivityStartTokenRepository extends JpaRepository<LiveActivityStartToken, Long> {

	Optional<LiveActivityStartToken> findByPushToken(String pushToken);

	/** 발송에 필요한 것만 뽑는다. 응원 팀은 회원마다 달라 카드 payload 도 회원별로 만든다. */
	interface StartTargetRow {
		String getPushToken();

		Long getMemberId();

		String getFavoriteTeamCode();
	}

	/**
	 * 이 경기의 카드를 새로 띄워 줄 대상.
	 *
	 * <p>대상 산정이 기존 갱신 발송과 반대다. 갱신은 "이미 카드를 띄운 토큰"에 보내지만,
	 * 생성은 "이 경기를 구독한 회원"에게 보내야 한다. 그래서 팀 구독·경기 구독을 그대로 따라간다
	 * (세트 시작 토글이 켜진 경우만 — 알림을 끈 사람에게 카드를 띄우지 않는다).</p>
	 *
	 * <p>이미 카드가 떠 있는 회원은 제외한다. 앱이 먼저 띄운 카드가 있는데 서버가 또 만들면
	 * 잠금화면에 같은 경기 카드가 두 장 뜬다.</p>
	 */
	@Query("""
			SELECT t.pushToken AS pushToken, m.id AS memberId, ft.code AS favoriteTeamCode
			FROM LiveActivityStartToken t
			JOIN t.member m
			LEFT JOIN m.favoriteTeam ft
			WHERE t.active = true
			  AND (
				  EXISTS (
					  SELECT ts.id FROM MemberTeamNotificationSubscription ts
					  WHERE ts.member = m
					    AND ts.setStartEnabled = true
					    AND ((:blueTeamId IS NOT NULL AND ts.team.id = :blueTeamId)
					      OR (:redTeamId IS NOT NULL AND ts.team.id = :redTeamId))
				  )
				  OR EXISTS (
					  SELECT ms.id FROM MemberMatchSubscription ms
					  WHERE ms.member = m
					    AND ms.matchId = :matchId
					    AND ms.setStartEnabled = true
				  )
			  )
			  AND NOT EXISTS (
				  SELECT a.id FROM LiveActivityToken a
				  WHERE a.member = m AND a.matchId = :matchId AND a.active = true
			  )
			""")
	List<StartTargetRow> findStartTargets(
			@Param("matchId") String matchId,
			@Param("blueTeamId") Long blueTeamId,
			@Param("redTeamId") Long redTeamId);

	/**
	 * 회원 한 명에게 이 경기 카드를 띄워 줄 대상(없으면 빈 목록).
	 *
	 * <p>{@link #findStartTargets} 와 달리 구독 조건을 다시 보지 않는다 — 호출 근거가
	 * "이 회원이 방금 이 경기를 구독했다"는 액션 자체이고, 그 구독 행은 아직 같은 트랜잭션
	 * 안에 있어(커밋 전) 이 쿼리로는 보이지 않을 수도 있다.</p>
	 *
	 * <p>이미 카드가 떠 있는 회원을 제외하는 조건은 그대로 둔다. 같은 경기 카드가 두 장 뜨는 것을
	 * 막는 유일한 장치다.</p>
	 */
	@Query("""
			SELECT t.pushToken AS pushToken, m.id AS memberId, ft.code AS favoriteTeamCode
			FROM LiveActivityStartToken t
			JOIN t.member m
			LEFT JOIN m.favoriteTeam ft
			WHERE t.active = true
			  AND m.id = :memberId
			  AND NOT EXISTS (
				  SELECT a.id FROM LiveActivityToken a
				  WHERE a.member = m AND a.matchId = :matchId AND a.active = true
			  )
			""")
	List<StartTargetRow> findStartTargetsForMember(
			@Param("matchId") String matchId,
			@Param("memberId") Long memberId);

	/** APNs 가 거절한(410 등) 토큰은 더 쓰지 않는다. */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update LiveActivityStartToken t set t.active = false, t.updatedAt = CURRENT_TIMESTAMP "
			+ "where t.pushToken in :pushTokens")
	int deactivateByPushTokenIn(@Param("pushTokens") List<String> pushTokens);
}
