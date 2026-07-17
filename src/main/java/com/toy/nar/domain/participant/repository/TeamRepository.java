package com.toy.nar.domain.participant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.toy.nar.domain.participant.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	@Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.leagueTeams")
	List<Team> findAllWithLeagueTeams();

	@Query("SELECT t FROM Team t WHERE t.name IN :names")
	List<Team> findAllByNameInIgnoreCase(@Param("names") Set<String> names);

	@Query("SELECT t FROM Team t LEFT JOIN FETCH t.leagueTeams WHERE t.name IN :names")
	List<Team> findAllByNameInWithLeagueTeams(@Param("names") Set<String> names);

	@Query("SELECT t FROM Team t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
	List<Team> findByNameContainingIgnoreCase(@Param("keyword") String keyword);

	@Query("SELECT t FROM Team t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) AND t.code IS NOT NULL")
	List<Team> findByNameContainingIgnoreCaseAndCodeIsNotNull(@Param("keyword") String keyword);

	@Query("SELECT t FROM Team t WHERE LOWER(t.code) = LOWER(:code) AND t.code IS NOT NULL")
	List<Team> findByCodeIgnoreCase(@Param("code") String code);

	@Query("SELECT t FROM Team t WHERE t.code IN :codes")
	List<Team> findAllByCodeIn(@Param("codes") Collection<String> codes);

	@Query("""
			SELECT DISTINCT t
			FROM LeagueTeam lt
			JOIN lt.team t
			WHERE lt.league.leagueName = :leagueName
			  AND lt.league.seasonYear = :year
			ORDER BY t.name
			""")
	List<Team> findOnboardingTeams(@Param("leagueName") String leagueName, @Param("year") int year);

	@Query("SELECT t FROM Team t WHERE LOWER(t.name) = LOWER(:name)")
	Optional<Team> findByNameIgnoreCase(@Param("name") String name);

	// 백오피스 검색: 팀명·코드 부분일치 + 리그 필터. q/league 가 null 이면 각 조건 무시.
	// 리그는 출전 기록(GameParticipant) 기준 EXISTS(전 시즌 통합).
	// ⚠️ league_teams 기준 금지: 오염돼 있음(LCK에 462팀) — 선수 검색과 동일 사유. 정리 전까지 출전 기록으로 판정.
	@Query("""
			SELECT t FROM Team t
			WHERE (:q IS NULL
			       OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%'))
			       OR LOWER(t.code) LIKE LOWER(CONCAT('%', :q, '%')))
			  AND (:league IS NULL
			       OR EXISTS (SELECT 1 FROM GameParticipant gp
			                  WHERE gp.team = t AND gp.game.league.leagueName = :league))
			""")
	Page<Team> searchForBackoffice(@Param("q") String q, @Param("league") String league, Pageable pageable);
}
