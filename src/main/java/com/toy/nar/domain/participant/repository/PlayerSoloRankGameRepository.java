package com.toy.nar.domain.participant.repository;

import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerSoloRankGameRepository extends JpaRepository<PlayerSoloRankGame, Long> {

	boolean existsByPlayer_IdAndGameId(Long playerId, String gameId);

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
