package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerSoloRankGameRepository extends JpaRepository<PlayerSoloRankGame, Long> {

	boolean existsByPlayer_IdAndGameId(Long playerId, String gameId);

	/** 종료 알림을 이미 낸 게임인지. 같은 게임에 Riot 조회를 두 번 태우지 않기 위한 게이트다. */
	boolean existsByPlayer_IdAndGameIdAndEndNotifiedAtIsNotNull(Long playerId, String gameId);

	@org.springframework.transaction.annotation.Transactional
	@org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
	@Query("UPDATE PlayerSoloRankGame g SET g.endNotifiedAt = :notifiedAt"
			+ " WHERE g.player.id = :playerId AND g.gameId = :gameId")
	int markEndNotified(
			@Param("playerId") Long playerId,
			@Param("gameId") String gameId,
			@Param("notifiedAt") java.time.LocalDateTime notifiedAt);

	/** 실제 시작 시각을 한 번만 채운다. 로딩 화면(시작 0)에 감지된 게임을 다음 폴링에서 메우는 용도. */
	@org.springframework.transaction.annotation.Transactional
	@org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
	@Query("UPDATE PlayerSoloRankGame g SET g.gameStartedAt = :startedAt"
			+ " WHERE g.player.id = :playerId AND g.gameId = :gameId AND g.gameStartedAt IS NULL")
	int fillStartedAt(
			@Param("playerId") Long playerId,
			@Param("gameId") String gameId,
			@Param("startedAt") java.time.LocalDateTime startedAt);

	/** match-v5 결과를 싣는다. 시작 시각도 match-v5 값이 정확해서 덮어쓴다. */
	@org.springframework.transaction.annotation.Transactional
	@org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
	@Query("UPDATE PlayerSoloRankGame g SET g.gameStartedAt = COALESCE(:startedAt, g.gameStartedAt),"
			+ " g.gameEndedAt = :endedAt, g.durationSeconds = :durationSeconds, g.win = :win,"
			+ " g.kills = :kills, g.deaths = :deaths, g.assists = :assists"
			+ " WHERE g.player.id = :playerId AND g.gameId = :gameId")
	int recordResult(
			@Param("playerId") Long playerId,
			@Param("gameId") String gameId,
			@Param("startedAt") java.time.LocalDateTime startedAt,
			@Param("endedAt") java.time.LocalDateTime endedAt,
			@Param("durationSeconds") Integer durationSeconds,
			@Param("win") Boolean win,
			@Param("kills") Integer kills,
			@Param("deaths") Integer deaths,
			@Param("assists") Integer assists);

	/**
	 * 지금 솔랭 중인 게임. 계정 상태가 IN_RANKED_SOLO 이고 그 계정이 보고 있는 게임과 이력 행이
	 * 맞물리는 것만 — 모니터가 멈춰 상태가 굳은 계정은 {@code freshSince} 로 걸러낸다.
	 */
	@Query("SELECT g FROM PlayerSoloRankGame g"
			+ " JOIN PlayerRiotAccount a ON a.player = g.player AND a.lastCheckedMatchId = g.gameId"
			+ " LEFT JOIN FETCH g.champion"
			+ " WHERE g.player.id IN :playerIds"
			+ " AND a.liveStatus = :status"
			+ " AND a.lastMatchCheckedAt >= :freshSince")
	List<PlayerSoloRankGame> findLive(
			@Param("playerIds") java.util.Collection<Long> playerIds,
			@Param("status") com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus status,
			@Param("freshSince") java.time.LocalDateTime freshSince);

	/**
	 * {@code endedSince} 이후 끝난 게임, 최근 종료순. {@code detectedSince} 는 (player_id, detected_at)
	 * 인덱스를 태우려는 범위다 — 감지는 종료보다 늘 앞서므로 종료 범위보다 넉넉히 잡아 넘긴다.
	 */
	@Query("SELECT g FROM PlayerSoloRankGame g LEFT JOIN FETCH g.champion"
			+ " WHERE g.player.id IN :playerIds"
			+ " AND g.detectedAt >= :detectedSince"
			+ " AND g.gameEndedAt >= :endedSince"
			+ " ORDER BY g.gameEndedAt DESC")
	List<PlayerSoloRankGame> findFinishedSince(
			@Param("playerIds") java.util.Collection<Long> playerIds,
			@Param("detectedSince") java.time.LocalDateTime detectedSince,
			@Param("endedSince") java.time.LocalDateTime endedSince);

	/** 선수의 최근 솔랭 게임(감지 최신순). 선수 카드 "최근 솔랭"용. */
	List<PlayerSoloRankGame> findTop20ByPlayer_IdOrderByDetectedAtDesc(Long playerId);

	/** 선수의 챔피언별 솔랭 플레이 횟수(많은 순). 선수 카드 "챔프 폭"용. */
	@Query("SELECT g.champion.id AS championId, COUNT(g) AS playCount "
			+ "FROM PlayerSoloRankGame g "
			+ "WHERE g.player.id = :playerId AND g.champion IS NOT NULL "
			+ "GROUP BY g.champion.id "
			+ "ORDER BY COUNT(g) DESC")
	List<ChampionPlayCount> findChampionPlayCounts(@Param("playerId") Long playerId);

	/** 챔프 폭 집계 projection. */
	interface ChampionPlayCount {
		Long getChampionId();

		long getPlayCount();
	}
}
