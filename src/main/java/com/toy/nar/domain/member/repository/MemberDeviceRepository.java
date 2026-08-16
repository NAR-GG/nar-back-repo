package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberDevice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberDeviceRepository extends JpaRepository<MemberDevice, Long> {

	Optional<MemberDevice> findByFcmToken(String fcmToken);

	Optional<MemberDevice> findByIdAndMember_Id(Long id, Long memberId);

	// 백오피스 회원 상세: 푸시 받을 수 있는 기기 수.
	long countByMember_IdAndActiveTrue(Long memberId);

	@EntityGraph(attributePaths = "member")
	@Query("""
			SELECT DISTINCT device
			FROM MemberDevice device
			WHERE device.active = true
			  AND EXISTS (
				  SELECT favorite.id
				  FROM MemberFavoritePlayer favorite
				  WHERE favorite.member = device.member
				    AND favorite.player.id = :playerId
			  )
			""")
	List<MemberDevice> findActiveDevicesBySubscribedPlayerId(@Param("playerId") Long playerId);

	/**
	 * 특정 팀을 구독하고 해당 이벤트 토글이 켜진 회원들의 활성 기기 목록.
	 * 라이브 FCM 푸시(#21) 전용. 토글은 eventType 에 따라 다른 컬럼을 본다(EXISTS + CASE).
	 * 기존 player 구독 쿼리(findActiveDevicesBySubscribedPlayerId)와 동일한 패턴이다.
	 *
	 * <p>{@code LIVE_EVENT} 는 마스터 스위치({@code liveEventEnabled})와 종류별 토글을 AND 로 본다.
	 * {@code eventSubType} 이 null 이면 종류를 모르는 것이므로 마스터만 보고 보낸다 — 세분화 이전
	 * 동작이고, 나중에 새 이벤트 종류(아타칸 등)가 컬럼 없이 들어와도 조용히 사라지지 않는다.
	 *
	 * @param eventSubType KILL/BARON/DRAGON/TOWER/INHIBITOR. LIVE_EVENT 가 아니면 무시된다
	 */
	@EntityGraph(attributePaths = "member")
	@Query("""
			SELECT DISTINCT device
			FROM MemberDevice device
			WHERE device.active = true
			  AND EXISTS (
				  SELECT subscription.id
				  FROM MemberTeamNotificationSubscription subscription
				  WHERE subscription.member = device.member
				    AND subscription.team.id = :teamId
				    AND (
				        (:eventType = 'SET_START' AND subscription.setStartEnabled = true)
				        OR (:eventType = 'SET_END' AND subscription.setEndEnabled = true)
				        OR (:eventType = 'LIVE_EVENT' AND subscription.liveEventEnabled = true AND (
				            :eventSubType IS NULL
				            OR (:eventSubType = 'KILL' AND subscription.killEnabled = true)
				            OR (:eventSubType = 'BARON' AND subscription.baronEnabled = true)
				            OR (:eventSubType = 'DRAGON' AND subscription.dragonEnabled = true)
				            OR (:eventSubType = 'TOWER' AND subscription.towerEnabled = true)
				            OR (:eventSubType = 'INHIBITOR' AND subscription.inhibitorEnabled = true)
				        ))
				    )
			  )
			""")
	List<MemberDevice> findActiveDevicesBySubscribedTeamId(
			@Param("teamId") Long teamId,
			@Param("eventType") String eventType,
			@Param("eventSubType") String eventSubType);

	/**
	 * 특정 경기를 예약 구독하고 해당 이벤트 토글이 켜진 회원들의 활성 기기 목록.
	 * 팀 구독(findActiveDevicesBySubscribedTeamId)과 동일한 패턴이다.
	 */
	@EntityGraph(attributePaths = "member")
	@Query("""
			SELECT DISTINCT device
			FROM MemberDevice device
			WHERE device.active = true
			  AND EXISTS (
				  SELECT subscription.id
				  FROM MemberMatchSubscription subscription
				  WHERE subscription.member = device.member
				    AND subscription.matchId = :matchId
				    AND (
				        (:eventType = 'SET_START' AND subscription.setStartEnabled = true)
				        OR (:eventType = 'SET_END' AND subscription.setEndEnabled = true)
				        OR (:eventType = 'LIVE_EVENT' AND subscription.liveEventEnabled = true AND (
				            :eventSubType IS NULL
				            OR (:eventSubType = 'KILL' AND subscription.killEnabled = true)
				            OR (:eventSubType = 'BARON' AND subscription.baronEnabled = true)
				            OR (:eventSubType = 'DRAGON' AND subscription.dragonEnabled = true)
				            OR (:eventSubType = 'TOWER' AND subscription.towerEnabled = true)
				            OR (:eventSubType = 'INHIBITOR' AND subscription.inhibitorEnabled = true)
				        ))
				    )
			  )
			""")
	List<MemberDevice> findActiveDevicesBySubscribedMatchId(
			@Param("matchId") String matchId,
			@Param("eventType") String eventType,
			@Param("eventSubType") String eventSubType);

	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("""
			UPDATE MemberDevice device
			SET device.active = false
			WHERE device.fcmToken IN :tokens
			""")
	int deactivateByFcmTokenIn(@Param("tokens") Collection<String> tokens);
}
