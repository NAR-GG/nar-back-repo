package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.PlayerSoloRankPushDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PlayerSoloRankPushDeliveryRepository
		extends JpaRepository<PlayerSoloRankPushDelivery, Long> {

/*
	 * [중복 발송 버그 수정] 기존 reserve 는 ON DUPLICATE KEY UPDATE + IF 패턴이라
	 * MySQL CLIENT_FOUND_ROWS(커넥터 기본) 모드에서 이미 SENT 인 건도 0이 아닌 값을
	 * 반환해 dedup 이 무력화됐다. INSERT IGNORE(신규) + WHERE 조건 UPDATE(재예약)로
	 * 분리해 found-rows 모드에서도 정확하게 동작한다.
	 */

	/** 신규 예약. 이미 같은 키가 있으면 0. */
	@Modifying
	@Transactional
	@Query(value = """
			INSERT IGNORE INTO player_solo_rank_push_delivery (
				member_id, player_id, game_id, status, created_at, updated_at
			) VALUES (
				:memberId, :playerId, :gameId, 'PENDING', NOW(), NOW()
			)
			""", nativeQuery = true)
	int insertIfAbsent(
			@Param("memberId") Long memberId,
			@Param("playerId") Long playerId,
			@Param("gameId") String gameId);

	/** 재예약. 실패했거나 5분 넘게 PENDING 으로 방치된 건만 되살린다. SENT 는 재예약되지 않는다. */
	@Modifying
	@Transactional
	@Query(value = """
			UPDATE player_solo_rank_push_delivery
			SET status = 'PENDING', error_message = NULL, updated_at = NOW()
			WHERE member_id = :memberId
			  AND player_id = :playerId
			  AND game_id = :gameId
			  AND (status = 'FAILED'
					OR (status = 'PENDING' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)))
			""", nativeQuery = true)
	int reactivateStale(
			@Param("memberId") Long memberId,
			@Param("playerId") Long playerId,
			@Param("gameId") String gameId);

	/** 발송 예약. 신규이거나 재시도 대상일 때만 true. */
	default boolean reserve(Long memberId, Long playerId, String gameId) {
		if (insertIfAbsent(memberId, playerId, gameId) > 0) {
			return true;
		}
		return reactivateStale(memberId, playerId, gameId) > 0;
	}

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
