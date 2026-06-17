package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.MemberTeamEventPushDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 라이브 경기 팀 이벤트 FCM 푸시(#21) 멱등 리포지토리.
 * PlayerSoloRankPushDeliveryRepository 의 reserve / markSent / markFailed 패턴을 복제했다.
 * 멱등 키는 (member_id, match_id, set_number, event_type, event_order).
 */
public interface MemberTeamEventPushDeliveryRepository
		extends JpaRepository<MemberTeamEventPushDelivery, Long> {

	@Modifying
	@Transactional
	@Query(value = """
			INSERT IGNORE INTO member_team_event_push_delivery (
				member_id,
				match_id,
				set_number,
				event_type,
				event_order,
				status,
				created_at,
				updated_at
			) VALUES (
				:memberId,
				:matchId,
				:setNumber,
				:eventType,
				:eventOrder,
				'PENDING',
				NOW(),
				NOW()
			)
			ON DUPLICATE KEY UPDATE
				error_message = IF(
					status = 'FAILED'
						OR (status = 'PENDING' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
					NULL,
					error_message
				),
				updated_at = IF(
					status = 'FAILED'
						OR (status = 'PENDING' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
					NOW(),
					updated_at
				),
				status = IF(
					status = 'FAILED'
						OR (status = 'PENDING' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
					'PENDING',
					status
				)
			""", nativeQuery = true)
	int reserve(
			@Param("memberId") Long memberId,
			@Param("matchId") String matchId,
			@Param("setNumber") int setNumber,
			@Param("eventType") String eventType,
			@Param("eventOrder") long eventOrder);

	@Modifying
	@Transactional
	@Query("""
			UPDATE MemberTeamEventPushDelivery delivery
			SET delivery.status = com.toy.nar.domain.member.entity.PushDeliveryStatus.SENT,
			    delivery.errorMessage = null,
			    delivery.sentAt = CURRENT_TIMESTAMP,
			    delivery.updatedAt = CURRENT_TIMESTAMP
			WHERE delivery.member.id = :memberId
			  AND delivery.matchId = :matchId
			  AND delivery.setNumber = :setNumber
			  AND delivery.eventType = :eventType
			  AND delivery.eventOrder = :eventOrder
			""")
	int markSent(
			@Param("memberId") Long memberId,
			@Param("matchId") String matchId,
			@Param("setNumber") int setNumber,
			@Param("eventType") String eventType,
			@Param("eventOrder") long eventOrder);

	@Modifying
	@Transactional
	@Query("""
			UPDATE MemberTeamEventPushDelivery delivery
			SET delivery.status = com.toy.nar.domain.member.entity.PushDeliveryStatus.FAILED,
			    delivery.errorMessage = :errorMessage,
			    delivery.updatedAt = CURRENT_TIMESTAMP
			WHERE delivery.member.id = :memberId
			  AND delivery.matchId = :matchId
			  AND delivery.setNumber = :setNumber
			  AND delivery.eventType = :eventType
			  AND delivery.eventOrder = :eventOrder
			""")
	int markFailed(
			@Param("memberId") Long memberId,
			@Param("matchId") String matchId,
			@Param("setNumber") int setNumber,
			@Param("eventType") String eventType,
			@Param("eventOrder") long eventOrder,
			@Param("errorMessage") String errorMessage);
}
