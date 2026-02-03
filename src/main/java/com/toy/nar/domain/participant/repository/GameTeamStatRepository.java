package com.toy.nar.domain.participant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.domain.participant.entity.GameTeamStat;

public interface GameTeamStatRepository extends JpaRepository<GameTeamStat, Long> {

	List<GameTeamStat> findByGameId(Long id);

	/**
	 * 특정 팀의 특정 연도 경기 통계 조회
	 */
	@Query("""
				SELECT gts FROM GameTeamStat gts
				JOIN FETCH gts.game g
				JOIN FETCH gts.team t
				WHERE gts.team.id = :teamId
				AND YEAR(g.actualGameStartTime) = :year
				ORDER BY g.actualGameStartTime
			""")
	List<GameTeamStat> findByTeamIdAndYear(@Param("teamId") Long teamId, @Param("year") int year);

	/**
	 * 특정 연도 모든 팀 통계 조회 (리그 평균 계산용)
	 */
	@Query("""
				SELECT gts FROM GameTeamStat gts
				JOIN FETCH gts.game g
				JOIN FETCH gts.team t
				WHERE YEAR(g.actualGameStartTime) = :year
			""")
	List<GameTeamStat> findByYear(@Param("year") int year);

	/**
	 * 특정 팀의 특정 연도 게임 ID 목록 조회
	 */
	@Query("""
				SELECT DISTINCT g.id FROM GameTeamStat gts
				JOIN gts.game g
				WHERE gts.team.id = :teamId
				AND YEAR(g.actualGameStartTime) = :year
			""")
	List<Long> findGameIdsByTeamIdAndYear(@Param("teamId") Long teamId, @Param("year") int year);

	/**
	 * LCK 리그만 필터링하여 조회
	 */
	@Query("""
				SELECT gts FROM GameTeamStat gts
				JOIN FETCH gts.game g
				JOIN FETCH gts.team t
				JOIN g.league l
				WHERE YEAR(g.actualGameStartTime) = :year
				AND l.leagueName = :leagueName
			""")
	List<GameTeamStat> findByYearAndLeagueName(@Param("year") int year, @Param("leagueName") String leagueName);
}
