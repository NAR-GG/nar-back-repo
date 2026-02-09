package com.toy.nar.app.category;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.toy.nar.app.category.dto.CategoryQueryDto;
import com.toy.nar.app.category.dto.CategoryTree;
import com.toy.nar.app.category.dto.LeagueCategory;
import com.toy.nar.app.category.dto.SeasonCategory;
import com.toy.nar.app.category.dto.SplitCategory;
import com.toy.nar.app.category.dto.TeamSummary;
import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.repository.LeagueRepository;
import com.toy.nar.domain.game.repository.LeagueTeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final LeagueRepository leagueRepository;
	private final LeagueTeamRepository leagueTeamRepository;

	public CategoryTree buildCategoryTree(int year) {
		List<CategoryQueryDto> flatData = leagueRepository.findAllCategoryDataByYear(year);
		List<LeagueCategory> leagueCategories = flatData.stream()
				.collect(Collectors.groupingBy(CategoryQueryDto::leagueName))
				.entrySet().stream()
				.map(leagueEntity -> {
					String leagueName = leagueEntity.getKey();

					List<SplitCategory> splitCategories = leagueEntity.getValue().stream()
							.collect(Collectors.groupingBy(CategoryQueryDto::splitName))
							.entrySet().stream()
							.map(splitEntry -> {
								String splitName = splitEntry.getKey();

								List<TeamSummary> teams = splitEntry.getValue().stream()
										.filter(dto -> dto.teamName() != null)
										.map(dto -> new TeamSummary(dto.teamId(), dto.teamName()))
										.distinct()
										.sorted(Comparator.comparing(TeamSummary::name))
										.toList();

								Long leagueId = splitEntry.getValue().get(0).leagueId();
								return new SplitCategory(splitName, leagueId, teams);
							}).toList();

					return new LeagueCategory(leagueName, splitCategories);
				}).toList();
		SeasonCategory seasonCategory = new SeasonCategory(year, leagueCategories);
		return new CategoryTree(List.of(seasonCategory));
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
						.orElseThrow(
								() -> new IllegalStateException("League not found for " + leagueName + " " + split)));

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
