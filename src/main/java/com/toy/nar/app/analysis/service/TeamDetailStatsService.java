package com.toy.nar.app.analysis.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.analysis.dto.TeamDetailStatsItemDto;
import com.toy.nar.app.analysis.dto.TeamDetailStatsResponse;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamDetailStatsService {

	private final GameTeamStatRepository gameTeamStatRepository;

	public TeamDetailStatsResponse getTeamDetailStats(
			String league,
			Integer year,
			String split,
			String patch,
			String side) {
		TeamAnalysisFilter filter = TeamAnalysisFilter.from(league, year, split, patch, side);

		List<Object[]> detailRows = gameTeamStatRepository.findTeamDetailStatsByFilter(
				filter.year(),
				filter.leagueName(),
				filter.split(),
				filter.patch(),
				filter.side());
		if (detailRows.isEmpty()) {
			throw new IllegalArgumentException(
					"No team detail stats found for league " + filter.leagueName() + " in year " + filter.year());
		}

		Map<Long, SeriesRecord> seriesByTeam = new HashMap<>();
		for (Object[] row : gameTeamStatRepository.findTeamSeriesStatsByFilter(
				filter.year(),
				filter.leagueName(),
				filter.split(),
				filter.patch(),
				filter.side())) {
			Long teamId = toLong(row[0]);
			seriesByTeam.put(teamId, new SeriesRecord(
					toInt(row[1]),
					toInt(row[2]),
					toInt(row[3])));
		}

		List<TeamDetailStatsItemDto> sorted = detailRows.stream()
				.map(row -> toItem(row, seriesByTeam.getOrDefault(toLong(row[0]), SeriesRecord.empty())))
				.sorted((a, b) -> {
					int byWinRate = Double.compare(
							b.getWinRatePct() != null ? b.getWinRatePct() : 0,
							a.getWinRatePct() != null ? a.getWinRatePct() : 0);
					if (byWinRate != 0) {
						return byWinRate;
					}
					int byMatchWins = Integer.compare(
							b.getMatchWins() != null ? b.getMatchWins() : 0,
							a.getMatchWins() != null ? a.getMatchWins() : 0);
					if (byMatchWins != 0) {
						return byMatchWins;
					}
					return (a.getTeamName() != null ? a.getTeamName() : "")
							.compareToIgnoreCase(b.getTeamName() != null ? b.getTeamName() : "");
				})
				.toList();

		AtomicInteger rank = new AtomicInteger(1);
		List<TeamDetailStatsItemDto> ranked = sorted.stream()
				.map(item -> TeamDetailStatsItemDto.builder()
						.rank(rank.getAndIncrement())
						.teamId(item.getTeamId())
						.teamName(item.getTeamName())
						.teamCode(item.getTeamCode())
						.teamImageUrl(item.getTeamImageUrl())
						.matchesPlayed(item.getMatchesPlayed())
						.matchWins(item.getMatchWins())
						.matchLosses(item.getMatchLosses())
						.setsPlayed(item.getSetsPlayed())
						.setWins(item.getSetWins())
						.setLosses(item.getSetLosses())
						.winRatePct(item.getWinRatePct())
						.avgKills(item.getAvgKills())
						.avgGold(item.getAvgGold())
						.avgBarons(item.getAvgBarons())
						.avgDragons(item.getAvgDragons())
						.avgTowers(item.getAvgTowers())
						.firstBloodCount(item.getFirstBloodCount())
						.firstTowerCount(item.getFirstTowerCount())
						.firstDragonCount(item.getFirstDragonCount())
						.firstHeraldCount(item.getFirstHeraldCount())
						.firstBaronCount(item.getFirstBaronCount())
						.firstBloodRatePct(item.getFirstBloodRatePct())
						.firstTowerRatePct(item.getFirstTowerRatePct())
						.firstDragonRatePct(item.getFirstDragonRatePct())
						.firstHeraldRatePct(item.getFirstHeraldRatePct())
						.firstBaronRatePct(item.getFirstBaronRatePct())
						.build())
				.toList();

		return TeamDetailStatsResponse.builder()
				.leagueName(filter.leagueName())
				.year(filter.year())
				.totalTeams(ranked.size())
				.items(ranked)
				.build();
	}

	private TeamDetailStatsItemDto toItem(Object[] row, SeriesRecord series) {
		Long teamId = toLong(row[0]);
		String teamName = toString(row[1]);
		String teamCode = toString(row[2]);
		String teamImageUrl = toString(row[3]);
		int setsPlayed = toInt(row[4]);
		int setWins = toInt(row[5]);
		int setLosses = toInt(row[6]);

		double winRate = series.matchesPlayed > 0
				? (series.matchWins * 100.0) / series.matchesPlayed
				: (setsPlayed > 0 ? (setWins * 100.0) / setsPlayed : 0);

		int firstBloodCount = toInt(row[12]);
		int firstTowerCount = toInt(row[13]);
		int firstDragonCount = toInt(row[14]);
		int firstHeraldCount = toInt(row[15]);
		int firstBaronCount = toInt(row[16]);

		return TeamDetailStatsItemDto.builder()
				.teamId(teamId)
				.teamName(teamName)
				.teamCode(teamCode)
				.teamImageUrl(teamImageUrl)
				.matchesPlayed(series.matchesPlayed)
				.matchWins(series.matchWins)
				.matchLosses(series.matchLosses)
				.setsPlayed(setsPlayed)
				.setWins(setWins)
				.setLosses(setLosses)
				.winRatePct(round(winRate, 2))
				.avgKills(round(toDouble(row[7]), 2))
				.avgGold(round(toDouble(row[8]), 0))
				.avgBarons(round(toDouble(row[9]), 2))
				.avgDragons(round(toDouble(row[10]), 2))
				.avgTowers(round(toDouble(row[11]), 2))
				.firstBloodCount(firstBloodCount)
				.firstTowerCount(firstTowerCount)
				.firstDragonCount(firstDragonCount)
				.firstHeraldCount(firstHeraldCount)
				.firstBaronCount(firstBaronCount)
				.firstBloodRatePct(setsPlayed > 0 ? round(firstBloodCount * 100.0 / setsPlayed, 1) : 0)
				.firstTowerRatePct(setsPlayed > 0 ? round(firstTowerCount * 100.0 / setsPlayed, 1) : 0)
				.firstDragonRatePct(setsPlayed > 0 ? round(firstDragonCount * 100.0 / setsPlayed, 1) : 0)
				.firstHeraldRatePct(setsPlayed > 0 ? round(firstHeraldCount * 100.0 / setsPlayed, 1) : 0)
				.firstBaronRatePct(setsPlayed > 0 ? round(firstBaronCount * 100.0 / setsPlayed, 1) : 0)
				.build();
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

	private record SeriesRecord(int matchesPlayed, int matchWins, int matchLosses) {
		private static SeriesRecord empty() {
			return new SeriesRecord(0, 0, 0);
		}
	}
}
