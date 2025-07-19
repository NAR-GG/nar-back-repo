package com.toy.nar.app.category;

import java.util.List;

import org.springframework.stereotype.Service;

import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.repository.LeagueRepository;
import com.toy.nar.domain.game.repository.LeagueTeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final LeagueRepository leagueRepository;
	private final LeagueTeamRepository leagueTeamRepository;

	public CategoryTree buildCategoryTree() {
		List<SeasonCategory> seasons = List.of(buildSeason2025());
		return new CategoryTree(seasons);
	}

	private SeasonCategory buildSeason2025() {
		List<String> leagueNames = leagueRepository.findDistinctLeagueNames();
		List<LeagueCategory> leagues = leagueNames.stream()
			.map(this::buildLeagueCategory)
			.toList();

		return new SeasonCategory(2025, leagues);
	}

	private LeagueCategory buildLeagueCategory(String leagueName) {
		List<String> splits = leagueRepository.findSplitsByLeague(leagueName, 2025);
		List<SplitCategory> splitCategories = splits.stream()
			.map(split -> buildSplitCategory(leagueName, split))
			.toList();

		return new LeagueCategory(leagueName, splitCategories);
	}

	private SplitCategory buildSplitCategory(String leagueName, String split) {
		// 정규시즌과 플레이오프 구분 없이 해당 리그-스플릿의 대표 League 선택
		League league = leagueRepository.findByLeagueNameAndSeasonSplitAndIsPlayoffs(leagueName, split, false)
			.orElseGet(() -> leagueRepository.findByLeagueNameAndSeasonSplitAndIsPlayoffs(leagueName, split, true)
				.orElseThrow(() -> new IllegalStateException("League not found for " + leagueName + " " + split)));

		List<TeamSummary> teams = getTeamSummaries(leagueName, split);

		return new SplitCategory(split, league.getId(), teams);
	}

	public List<TeamSummary> getTeamSummaries(String leagueName, String split) {
		return leagueTeamRepository.findTeamsByLeagueParams(leagueName, 2025, split)
			.stream()
			.map(team -> new TeamSummary(team.getId(), team.getName()))
			.toList();
	}
}
