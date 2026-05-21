package com.toy.nar.domain.game.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.app.category.dto.CategoryPatchQueryDto;
import com.toy.nar.app.category.dto.CategoryQueryDto;
import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.participant.entity.GameTeamStat;

public interface LeagueRepository extends JpaRepository<League, Long> {

	@Query("SELECT DISTINCT new com.toy.nar.app.category.dto.CategoryQueryDto(l.leagueName, l.seasonSplit, l.id, t.id, t.name) "
			+
			"FROM GameTeamStat gts " +
			"JOIN gts.game g " +
			"JOIN g.league l " +
			"JOIN gts.team t " +
			"WHERE l.seasonYear = :year")
	List<CategoryQueryDto> findAllCategoryDataByYear(@Param("year") int year);

	@Query("SELECT DISTINCT new com.toy.nar.app.category.dto.CategoryPatchQueryDto(l.leagueName, l.seasonSplit, g.patch) "
			+
			"FROM Game g " +
			"JOIN g.league l " +
			"WHERE l.seasonYear = :year")
	List<CategoryPatchQueryDto> findDistinctPatchesByYear(@Param("year") int year);

	@Query("SELECT DISTINCT l.leagueName FROM League l WHERE l.seasonYear = 2025")
	List<String> findDistinctLeagueNames();

	@Query("SELECT DISTINCT l.leagueName FROM League l WHERE l.seasonYear = :year ORDER BY l.leagueName")
	List<String> findDistinctLeagueNamesByYear(@Param("year") int year);

	@Query("SELECT DISTINCT l.seasonSplit FROM League l WHERE l.leagueName = :leagueName AND l.seasonYear = :seasonYear")
	List<String> findSplitsByLeague(@Param("leagueName") String leagueName, @Param("seasonYear") Integer seasonYear);

	Optional<League> findByLeagueNameAndSeasonYearAndSeasonSplitAndIsPlayoffs(
			String leagueName, int seasonYear, String seasonSplit, boolean isPlayoffs);

	@Query("SELECT l FROM League l WHERE l.leagueName = :leagueName AND l.seasonSplit = :seasonSplit AND l.isPlayoffs = :isPlayoffs")
	Optional<League> findByLeagueNameAndSeasonSplitAndIsPlayoffs(@Param("leagueName") String leagueName,
			@Param("seasonSplit") String seasonSplit, @Param("isPlayoffs") Boolean isPlayoffs);

	@Query("SELECT DISTINCT l FROM League l LEFT JOIN FETCH l.leagueTeams WHERE l.leagueName IN :leagueNames AND l.seasonYear IN :years")
	List<League> findLeaguesWithTeamsByIdentifiers(
			@Param("leagueNames") Set<String> leagueNames,
			@Param("years") Set<Integer> years);
}
