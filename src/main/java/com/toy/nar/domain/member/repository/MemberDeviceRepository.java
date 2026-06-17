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
	 * 특정 팀을 구독하고 해당 이벤트 토글(SET_START/SET_END/LIVE_EVENT)이 켜진 회원들의 활성 기기 목록.
	 * 라이브 FCM 푸시(#21) 전용. 토글은 eventType 에 따라 다른 컬럼을 본다(EXISTS + CASE).
	 * 기존 player 구독 쿼리(findActiveDevicesBySubscribedPlayerId)와 동일한 패턴이다.
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
				        OR (:eventType = 'LIVE_EVENT' AND subscription.liveEventEnabled = true)
				    )
			  )
			""")
	List<MemberDevice> findActiveDevicesBySubscribedTeamId(
			@Param("teamId") Long teamId,
			@Param("eventType") String eventType);

	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("""
			UPDATE MemberDevice device
			SET device.active = false
			WHERE device.fcmToken IN :tokens
			""")
	int deactivateByFcmTokenIn(@Param("tokens") Collection<String> tokens);
}
