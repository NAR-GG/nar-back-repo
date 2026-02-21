package com.toy.nar.app.analysis.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.analysis.dto.TeamScatterMetric;
import com.toy.nar.app.analysis.dto.TeamScatterPointDto;
import com.toy.nar.app.analysis.dto.TeamScatterResponse;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamScatterService {

	private final GameTeamStatRepository gameTeamStatRepository;

	public TeamScatterResponse getTeamScatterStats(String leagueName, int year, String metric) {
		String normalizedLeague = normalizeLeagueName(leagueName);
		TeamScatterMetric scatterMetric = TeamScatterMetric.from(metric);

		List<Object[]> rows = gameTeamStatRepository.findTeamScatterStatsByLeagueAndYear(year, normalizedLeague);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException(
					"No team stats found for league " + normalizedLeague + " in year " + year);
		}

		List<TeamScatterPointDto> points = rows.stream()
				.map(row -> toPoint(row, scatterMetric))
				.sorted(Comparator.comparing(TeamScatterPointDto::getXValue).reversed())
				.toList();

		double totalGames = points.stream()
				.mapToDouble(p -> p.getGamesPlayed() != null ? p.getGamesPlayed() : 0)
				.sum();

		double weightedWins = points.stream()
				.mapToDouble(p -> (p.getWinRatePct() != null ? p.getWinRatePct() : 0)
						* (p.getGamesPlayed() != null ? p.getGamesPlayed() : 0))
				.sum();

		double weightedX = points.stream()
				.mapToDouble(p -> (p.getXValue() != null ? p.getXValue() : 0)
						* (p.getGamesPlayed() != null ? p.getGamesPlayed() : 0))
				.sum();

		double yLeagueAverage = totalGames > 0 ? weightedWins / totalGames : 0;
		double xLeagueAverage = totalGames > 0 ? weightedX / totalGames : 0;

		return TeamScatterResponse.builder()
				.leagueName(normalizedLeague)
				.year(year)
				.metric(scatterMetric)
				.xAxisLabel(scatterMetric.getAxisLabel())
				.yAxisLabel("Win Rate (%)")
				.xLeagueAverage(round(xLeagueAverage, 2))
				.yLeagueAverage(round(yLeagueAverage, 2))
				.points(points)
				.build();
	}

	private TeamScatterPointDto toPoint(Object[] row, TeamScatterMetric metric) {
		Long teamId = toLong(row[0]);
		String teamName = toString(row[1]);
		String teamCode = toString(row[2]);
		String teamImageUrl = toString(row[3]);
		int gamesPlayed = toInt(row[4]);
		int wins = toInt(row[5]);
		double avgOverall = toDouble(row[6]);
		double avgKills = toDouble(row[7]);
		double avgGold = toDouble(row[8]);
		double avgObjectives = toDouble(row[9]);

		double winRatePct = gamesPlayed > 0 ? (wins * 100.0) / gamesPlayed : 0;
		double xValue = switch (metric) {
			case ALL -> avgOverall;
			case KILLS -> avgKills;
			case GOLD -> avgGold;
			case OBJECTIVES -> avgObjectives;
		};

		return TeamScatterPointDto.builder()
				.teamId(teamId)
				.teamName(teamName)
				.teamCode(teamCode)
				.teamImageUrl(teamImageUrl)
				.gamesPlayed(gamesPlayed)
				.winRatePct(round(winRatePct, 2))
				.xValue(round(xValue, 2))
				.avgOverall(round(avgOverall, 2))
				.avgKills(round(avgKills, 2))
				.avgGold(round(avgGold, 2))
				.avgObjectives(round(avgObjectives, 2))
				.build();
	}

	private String normalizeLeagueName(String leagueName) {
		if (leagueName == null || leagueName.isBlank()) {
			return "LCK";
		}
		return leagueName.trim().toUpperCase();
	}

	private double round(double value, int scale) {
		double factor = Math.pow(10, scale);
		return Math.round(value * factor) / factor;
	}

	private Long toLong(Object value) {
		if (value == null) {
			return null;
		}
		return ((Number) value).longValue();
	}

	private int toInt(Object value) {
		if (value == null) {
			return 0;
		}
		return ((Number) value).intValue();
	}

	private double toDouble(Object value) {
		if (value == null) {
			return 0;
		}
		return ((Number) value).doubleValue();
	}

	private String toString(Object value) {
		return value == null ? null : value.toString();
	}
}
