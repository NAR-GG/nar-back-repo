package com.toy.nar.domain.game.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.toy.nar.domain.game.entity.League;

public interface LeagueRepository extends JpaRepository<League, Long> {

	@Query("SELECT DISTINCT l.leagueName FROM League l WHERE l.seasonYear = 2025")
	List<String> findDistinctLeagueNames();

	@Query("SELECT DISTINCT l.seasonSplit FROM League l WHERE l.leagueName = :leagueName AND l.seasonYear = :seasonYear")
	List<String> findSplitsByLeague(@Param("leagueName") String leagueName, @Param("seasonYear") Integer seasonYear);

	@Modifying
	@Query("DELETE FROM League l WHERE l.leagueName = :leagueName")
	int deleteByLeagueName(@Param("leagueName") String leagueName);

	@Query("SELECT l FROM League l WHERE l.leagueName = :leagueName AND l.seasonSplit = :seasonSplit AND l.isPlayoffs = :isPlayoffs")
	Optional<League> findByLeagueNameAndSeasonSplitAndIsPlayoffs(@Param("leagueName") String leagueName, @Param("seasonSplit") String seasonSplit, @Param("isPlayoffs") Boolean isPlayoffs);
}