package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.PlayerSoloRankPushDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PlayerSoloRankPushDeliveryRepository
		extends JpaRepository<PlayerSoloRankPushDelivery, Long> {

	@Modifying
	@Transactional
	@Query(value = """
			INSERT IGNORE INTO player_solo_rank_push_delivery (
				member_id,
				player_id,
				game_id,
				status,
				created_at,
				updated_at
			) VALUES (
				:memberId,
				:playerId,
				:gameId,
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
			@Param("playerId") Long playerId,
			@Param("gameId") String gameId);

	@Modifying
	@Transactional
	@Query("""
			UPDATE PlayerSoloRankPushDelivery delivery
			SET delivery.status = com.toy.nar.domain.member.entity.PushDeliveryStatus.SENT,
			    delivery.errorMessage = null,
			    delivery.sentAt = CURRENT_TIMESTAMP,
			    delivery.updatedAt = CURRENT_TIMESTAMP
			WHERE delivery.member.id = :memberId
			  AND delivery.player.id = :playerId
			  AND delivery.gameId = :gameId
			""")
	int markSent(
			@Param("memberId") Long memberId,
			@Param("playerId") Long playerId,
			@Param("gameId") String gameId);

	@Modifying
	@Transactional
	@Query("""
			UPDATE PlayerSoloRankPushDelivery delivery
			SET delivery.status = com.toy.nar.domain.member.entity.PushDeliveryStatus.FAILED,
			    delivery.errorMessage = :errorMessage,
			    delivery.updatedAt = CURRENT_TIMESTAMP
			WHERE delivery.member.id = :memberId
			  AND delivery.player.id = :playerId
			  AND delivery.gameId = :gameId
			""")
	int markFailed(
			@Param("memberId") Long memberId,
			@Param("playerId") Long playerId,
			@Param("gameId") String gameId,
			@Param("errorMessage") String errorMessage);
}
