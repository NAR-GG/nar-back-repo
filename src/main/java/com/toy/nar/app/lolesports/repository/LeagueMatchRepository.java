package com.toy.nar.app.lolesports.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LeagueMatchRepository extends JpaRepository<LeagueMatch, String> {

	@Query("SELECT m FROM LeagueMatch m WHERE m.leagueName = :leagueName ORDER BY m.matchDate DESC")
	List<LeagueMatch> findByLeagueNameOrderByMatchDateDesc(@Param("leagueName") String leagueName, Pageable pageable);

	@Query("SELECT m FROM LeagueMatch m WHERE m.leagueName = :leagueName AND m.matchDate < :olderThan ORDER BY m.matchDate DESC")
	List<LeagueMatch> findByLeagueNameAndDateBefore(@Param("leagueName") String leagueName, @Param("olderThan") LocalDateTime olderThan, Pageable pageable);

	List<LeagueMatch> findTop3ByOrderByMatchDateDesc(); // 전체 리그 기준 최신 3개 (기본값용)

	List<LeagueMatch> findTop3ByLeagueNameOrderByMatchDateDesc(String leagueName);

	@Query("SELECT m FROM LeagueMatch m WHERE m.leagueName = :leagueName AND m.matchDate >= :start AND m.matchDate <= :end ORDER BY m.matchDate DESC")
	List<LeagueMatch> findByLeagueNameAndDateRange(@Param("leagueName") String leagueName, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	@Query("SELECT m FROM LeagueMatch m WHERE m.matchDate >= :start AND m.matchDate <= :end ORDER BY m.matchDate DESC")
	List<LeagueMatch> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	@Query("""
			SELECT m
			FROM LeagueMatch m
			WHERE m.leagueName = :leagueName
			  AND m.matchDate >= :start
			  AND m.matchDate <= :end
			  AND (
					LOWER(m.blueTeamName) = LOWER(:teamName)
					OR LOWER(m.redTeamName) = LOWER(:teamName)
					OR (:teamCode IS NOT NULL AND m.blueTeamCode IS NOT NULL AND LOWER(m.blueTeamCode) = LOWER(:teamCode))
					OR (:teamCode IS NOT NULL AND m.redTeamCode IS NOT NULL AND LOWER(m.redTeamCode) = LOWER(:teamCode))
			  )
			ORDER BY m.matchDate DESC
			""")
	List<LeagueMatch> findTeamMatchesInDateRange(
			@Param("leagueName") String leagueName,
			@Param("teamName") String teamName,
			@Param("teamCode") String teamCode,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end,
			Pageable pageable);
}
