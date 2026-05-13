package com.toy.nar.app.analysis.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.analysis.dto.TeamBanStatDto;
import com.toy.nar.app.analysis.dto.TeamBanSideStatsDto;
import com.toy.nar.app.analysis.dto.TeamDashboardFilterDto;
import com.toy.nar.app.analysis.dto.TeamDashboardResponse;
import com.toy.nar.app.analysis.dto.TeamGameSummaryDto;
import com.toy.nar.app.analysis.dto.TeamPlayedChampionPlayerDto;
import com.toy.nar.app.analysis.dto.TeamPlayedChampionSideStatsDto;
import com.toy.nar.app.analysis.dto.TeamPlayedChampionStatDto;
import com.toy.nar.app.analysis.dto.TeamPlayerRecordDto;
import com.toy.nar.domain.game.repository.BanRepository;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.GameTeamStatRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamDashboardService {

	private static final int TOP_BAN_LIMIT = 5;
	private static final String SIDE_BLUE = "BLUE";
	private static final String SIDE_RED = "RED";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final TeamRepository teamRepository;
	private final GameTeamStatRepository gameTeamStatRepository;
	private final GameParticipantRepository gameParticipantRepository;
	private final BanRepository banRepository;
	
	// DB 집계 I/O가 서로 독립적이라 가상 스레드 기반 applicationTaskExecutor로 병렬화한다.
	@Qualifier("applicationTaskExecutor")
	private final Executor applicationTaskExecutor;

	public TeamDashboardResponse getTeamDashboard(Long teamId, String league, Integer year, String split, String patch,
			String side) {
		// 메인 스레드에서 즉시 조회
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

		String leagueName = normalizeLeague(league);
		String normalizedSide = normalizeSide(side);
		Integer normalizedYear = year != null ? year : 2026;
		String normalizedSplit = normalizeBlank(split);
		String normalizedPatch = normalizeBlank(patch);

		// 각 독립적인 통계 집계를 비동기로 실행 (Scatter)
		CompletableFuture<TeamGameSummaryDto> summaryFuture = CompletableFuture.supplyAsync(() -> 
				buildSummary(teamId, leagueName, normalizedYear, normalizedSplit, normalizedPatch, null), 
				applicationTaskExecutor);

		CompletableFuture<List<TeamPlayerRecordDto>> playerRecordsFuture = CompletableFuture.supplyAsync(() -> 
				buildPlayerRecords(teamId, leagueName, normalizedYear, normalizedSplit, normalizedPatch, normalizedSide), 
				applicationTaskExecutor);

		CompletableFuture<TeamPlayedChampionSideStatsDto> playedChampionsFuture = CompletableFuture.supplyAsync(() -> 
				buildPlayedChampionSideStats(teamId, leagueName, normalizedYear, normalizedSplit, normalizedPatch), 
				applicationTaskExecutor);

		// 밴 통계는 요약 통계(setsPlayed)에 의존하므로 summaryFuture 완료 이후 후속 작업으로 실행
		CompletableFuture<TeamBanSideStatsDto> bannedByTeamFuture = summaryFuture.thenApplyAsync(summary -> 
				buildBanSideStats(teamId, leagueName, normalizedYear, normalizedSplit, normalizedPatch, summary.getSetsPlayed(), true), 
				applicationTaskExecutor);

		CompletableFuture<TeamBanSideStatsDto> bannedAgainstFuture = summaryFuture.thenApplyAsync(summary -> 
				buildBanSideStats(teamId, leagueName, normalizedYear, normalizedSplit, normalizedPatch, summary.getSetsPlayed(), false), 
				applicationTaskExecutor);

		// 모든 비동기 작업이 완료될 때까지 대기 (Gather)
		CompletableFuture.allOf(summaryFuture, playerRecordsFuture, playedChampionsFuture, bannedByTeamFuture, bannedAgainstFuture).join();

		return TeamDashboardResponse.builder()
				.teamId(team.getId())
				.teamName(team.getName())
				.teamCode(team.getCode())
				.teamImageUrl(team.getImageUrl())
				.leagueName(leagueName)
				.appliedFilter(TeamDashboardFilterDto.builder()
						.year(normalizedYear)
						.split(normalizedSplit)
						.patch(normalizedPatch)
						.side(normalizedSide != null ? normalizedSide : "ALL")
						.build())
				.gameSummary(summaryFuture.join())
				.playerRecords(playerRecordsFuture.join())
				.bannedAgainst(bannedAgainstFuture.join())
				.bannedByTeam(bannedByTeamFuture.join())
				.playedChampions(playedChampionsFuture.join())
				.build();
	}

	private TeamGameSummaryDto buildSummary(Long teamId, String leagueName, Integer year, String split, String patch,
			String side) {
		Object[] summary = firstRow(
				gameTeamStatRepository.findTeamDashboardSummary(teamId, leagueName, year, split, patch, side));
		Object[] series = firstRow(
				gameTeamStatRepository.findTeamDashboardSeriesSummary(teamId, leagueName, year, split, patch, side));

		int setsPlayed = summary == null ? 0 : toInt(summary[0]);
		int setWins = summary == null ? 0 : toInt(summary[1]);
		int setLosses = summary == null ? 0 : toInt(summary[2]);
		int matchesPlayed = series == null ? 0 : toInt(series[0]);
		int matchWins = series == null ? 0 : toInt(series[1]);
		int matchLosses = series == null ? 0 : toInt(series[2]);

		double winRatePct = matchesPlayed > 0 ? (matchWins * 100.0) / matchesPlayed
				: (setsPlayed > 0 ? (setWins * 100.0) / setsPlayed : 0);

		return TeamGameSummaryDto.builder()
				.matchesPlayed(matchesPlayed)
				.matchWins(matchWins)
				.matchLosses(matchLosses)
				.setsPlayed(setsPlayed)
				.setWins(setWins)
				.setLosses(setLosses)
				.winRatePct(round(winRatePct, 2))
				.avgKills(summary == null ? 0 : round(toDouble(summary[3]), 2))
				.avgGold(summary == null ? 0 : round(toDouble(summary[4]), 0))
				.avgGameLengthSeconds(summary == null ? 0 : round(toDouble(summary[5]), 0))
				.avgBarons(summary == null ? 0 : round(toDouble(summary[6]), 2))
				.avgDragons(summary == null ? 0 : round(toDouble(summary[7]), 2))
				.avgTowers(summary == null ? 0 : round(toDouble(summary[8]), 2))
				.firstBloodCount(summary == null ? 0 : toInt(summary[9]))
				.firstTowerCount(summary == null ? 0 : toInt(summary[10]))
				.firstDragonCount(summary == null ? 0 : toInt(summary[11]))
				.firstHeraldCount(summary == null ? 0 : toInt(summary[12]))
				.firstBaronCount(summary == null ? 0 : toInt(summary[13]))
				.firstBloodRatePct(setsPlayed > 0 ? round(toInt(summary[9]) * 100.0 / setsPlayed, 1) : 0)
				.firstTowerRatePct(setsPlayed > 0 ? round(toInt(summary[10]) * 100.0 / setsPlayed, 1) : 0)
				.firstDragonRatePct(setsPlayed > 0 ? round(toInt(summary[11]) * 100.0 / setsPlayed, 1) : 0)
				.firstHeraldRatePct(setsPlayed > 0 ? round(toInt(summary[12]) * 100.0 / setsPlayed, 1) : 0)
				.firstBaronRatePct(setsPlayed > 0 ? round(toInt(summary[13]) * 100.0 / setsPlayed, 1) : 0)
				.build();
	}

	private List<TeamPlayerRecordDto> buildPlayerRecords(Long teamId, String leagueName, Integer year, String split,
			String patch, String side) {
		List<Object[]> rows = gameParticipantRepository.findTeamPlayerRecords(
				teamId, leagueName, year, split, patch, side);

		List<TeamPlayerRecordDto> records = new ArrayList<>();
		for (Object[] row : rows) {
			int gamesPlayed = toInt(row[4]);
			int wins = toInt(row[5]);
			int losses = gamesPlayed - wins;
			double totalKills = toDouble(row[6]);
			double totalDeaths = toDouble(row[7]);
			double totalAssists = toDouble(row[8]);
			double kda = (totalKills + totalAssists) / Math.max(totalDeaths, 1.0);

			records.add(TeamPlayerRecordDto.builder()
					.playerId(toLong(row[0]))
					.playerName(toString(row[1]))
					.playerImageUrl(toString(row[2]))
					.position(normalizePosition(toString(row[3])))
					.gamesPlayed(gamesPlayed)
					.wins(wins)
					.losses(losses)
					.winRatePct(gamesPlayed > 0 ? round((wins * 100.0) / gamesPlayed, 1) : 0)
					.avgKda(round(kda, 2))
					.avgKills(gamesPlayed > 0 ? round(totalKills / gamesPlayed, 2) : 0)
					.avgDeaths(gamesPlayed > 0 ? round(totalDeaths / gamesPlayed, 2) : 0)
					.avgAssists(gamesPlayed > 0 ? round(totalAssists / gamesPlayed, 2) : 0)
					.firstKillCount(toInt(row[9]))
					.firstDeathCount(toInt(row[10]))
					.pentaKillCount(toInt(row[11]))
					.avgKillParticipationPct(round(toDouble(row[12]), 1))
					.avgDamageSharePct(round(toDouble(row[13]), 1))
					.avgGoldSharePct(round(toDouble(row[14]), 1))
					.avgVisionScore(round(toDouble(row[15]), 2))
					.avgVisionScorePerMinute(round(toDouble(row[16]), 2))
					.build());
		}
		return records;
	}

	private List<TeamBanStatDto> buildBanStats(List<Object[]> rows, Integer setsPlayed) {
		int denominator = setsPlayed != null ? setsPlayed : 0;
		List<TeamBanStatDto> result = new ArrayList<>();
		for (Object[] row : rows) {
			if (result.size() >= TOP_BAN_LIMIT) {
				break;
			}
			int banCount = toInt(row[5]); // 4 -> 5 로 변경 (side 필드 추가됨)
			double rate = denominator > 0 ? (banCount * 100.0) / denominator : 0;
			result.add(TeamBanStatDto.builder()
					.championId(toLong(row[0]))
					.championNameKr(toString(row[1]))
					.championNameEn(toString(row[2]))
					.championImageUrl(toString(row[3]))
					.banCount(banCount)
					.banRatePct(round(rate, 1))
					.build());
		}
		return result;
	}

	private TeamBanSideStatsDto buildBanSideStats(Long teamId, String leagueName, Integer year, String split, String patch,
			Integer setsPlayed, boolean bannedByTeam) {
		
		List<Object[]> rows = bannedByTeam 
				? banRepository.findBansByTeamGroupedBySide(teamId, leagueName, year, split, patch)
				: banRepository.findBansAgainstTeamGroupedBySide(teamId, leagueName, year, split, patch);
				
		List<Object[]> blueRows = new ArrayList<>();
		List<Object[]> redRows = new ArrayList<>();
		Map<Long, Object[]> allStatsMap = new LinkedHashMap<>();

		for (Object[] row : rows) {
			String side = toString(row[4]);
			int count = toInt(row[5]);
			
			if (SIDE_BLUE.equalsIgnoreCase(side)) {
				blueRows.add(row);
			} else if (SIDE_RED.equalsIgnoreCase(side)) {
				redRows.add(row);
			}
			
			// 전체(ALL) 집계
			Long champId = toLong(row[0]);
			Object[] existing = allStatsMap.get(champId);
			if (existing == null) {
				allStatsMap.put(champId, new Object[] { row[0], row[1], row[2], row[3], "ALL", count });
			} else {
				existing[5] = toInt(existing[5]) + count;
			}
		}

		List<Object[]> allRows = new ArrayList<>(allStatsMap.values());
		allRows.sort((a, b) -> {
			int countCompare = Integer.compare(toInt(b[5]), toInt(a[5]));
			return countCompare != 0 ? countCompare : toString(a[2]).compareTo(toString(b[2]));
		});
		blueRows.sort((a, b) -> {
			int countCompare = Integer.compare(toInt(b[5]), toInt(a[5]));
			return countCompare != 0 ? countCompare : toString(a[2]).compareTo(toString(b[2]));
		});
		redRows.sort((a, b) -> {
			int countCompare = Integer.compare(toInt(b[5]), toInt(a[5]));
			return countCompare != 0 ? countCompare : toString(a[2]).compareTo(toString(b[2]));
		});

		return TeamBanSideStatsDto.builder()
				.all(buildBanStats(allRows, setsPlayed))
				.blue(buildBanStats(blueRows, setsPlayed))
				.red(buildBanStats(redRows, setsPlayed))
				.build();
	}

	private TeamPlayedChampionSideStatsDto buildPlayedChampionSideStats(Long teamId, String leagueName, Integer year, String split, String patch) {
		List<Object[]> rows = gameParticipantRepository.findTeamPlayedChampionsGroupedBySide(teamId, leagueName, year, split, patch);
		
		List<Object[]> blueRows = new ArrayList<>();
		List<Object[]> redRows = new ArrayList<>();
		Map<String, Object[]> allRowsMap = new LinkedHashMap<>();

		for (Object[] row : rows) {
			String side = toString(row[14]);
			if (SIDE_BLUE.equalsIgnoreCase(side)) blueRows.add(row);
			if (SIDE_RED.equalsIgnoreCase(side)) redRows.add(row);
			
			String key = toLong(row[0]) + ":" + toString(row[3]) + ":" + toLong(row[4]); // playerId:position:championId
			Object[] existing = allRowsMap.get(key);
			if (existing == null) {
				Object[] newRow = new Object[15];
				System.arraycopy(row, 0, newRow, 0, row.length);
				allRowsMap.put(key, newRow);
			} else {
				existing[8] = toInt(existing[8]) + toInt(row[8]);
				existing[9] = toInt(existing[9]) + toInt(row[9]);
				existing[10] = toDouble(existing[10]) + toDouble(row[10]);
				existing[11] = toDouble(existing[11]) + toDouble(row[11]);
				existing[12] = toDouble(existing[12]) + toDouble(row[12]);
				Timestamp t1 = (Timestamp) existing[13];
				Timestamp t2 = (Timestamp) row[13];
				if (t2 != null && (t1 == null || t2.after(t1))) {
					existing[13] = t2;
				}
			}
		}

		return TeamPlayedChampionSideStatsDto.builder()
				.all(buildPlayedChampionsFromRows(new ArrayList<>(allRowsMap.values())))
				.blue(buildPlayedChampionsFromRows(blueRows))
				.red(buildPlayedChampionsFromRows(redRows))
				.build();
	}

	private List<TeamPlayedChampionPlayerDto> buildPlayedChampionsFromRows(List<Object[]> rows) {
		Map<String, TeamPlayedChampionPlayerDto.TeamPlayedChampionPlayerDtoBuilder> playerBuilderByKey = new LinkedHashMap<>();
		Map<String, List<TeamPlayedChampionStatDto>> championListByPlayerKey = new LinkedHashMap<>();

		for (Object[] row : rows) {
			Long playerId = toLong(row[0]);
			String playerName = toString(row[1]);
			String playerImageUrl = toString(row[2]);
			String position = normalizePosition(toString(row[3]));
			String playerKey = playerId + ":" + position;

			playerBuilderByKey.computeIfAbsent(playerKey, ignored -> TeamPlayedChampionPlayerDto.builder()
					.playerId(playerId)
					.playerName(playerName)
					.playerImageUrl(playerImageUrl)
					.position(position));

			int gamesPlayed = toInt(row[8]);
			int wins = toInt(row[9]);
			double totalKills = toDouble(row[10]);
			double totalDeaths = toDouble(row[11]);
			double totalAssists = toDouble(row[12]);
			double avgKda = (totalKills + totalAssists) / Math.max(totalDeaths, 1.0);

			championListByPlayerKey.computeIfAbsent(playerKey, ignored -> new ArrayList<>())
					.add(TeamPlayedChampionStatDto.builder()
							.championId(toLong(row[4]))
							.championNameKr(toString(row[5]))
							.championNameEn(toString(row[6]))
							.championImageUrl(toString(row[7]))
							.gamesPlayed(gamesPlayed)
							.winRatePct(gamesPlayed > 0 ? round((wins * 100.0) / gamesPlayed, 1) : 0)
							.avgKda(round(avgKda, 2))
							.lastUsedAt(formatTimestampToKstDate(row[13]))
							.build());
		}

		List<TeamPlayedChampionPlayerDto> result = new ArrayList<>();
		for (Map.Entry<String, TeamPlayedChampionPlayerDto.TeamPlayedChampionPlayerDtoBuilder> entry : playerBuilderByKey
				.entrySet()) {
			List<TeamPlayedChampionStatDto> champions = championListByPlayerKey.getOrDefault(entry.getKey(),
					new ArrayList<>());
					
			champions.sort((a, b) -> {
				int cmp = Integer.compare(b.getGamesPlayed(), a.getGamesPlayed());
				if (cmp != 0) return cmp;
				return a.getChampionNameEn().compareTo(b.getChampionNameEn());
			});
			
			result.add(entry.getValue().champions(champions).build());
		}

		result.sort((a, b) -> {
			return Integer.compare(getPositionOrder(a.getPosition()), getPositionOrder(b.getPosition()));
		});

		return result;
	}
	
	private int getPositionOrder(String position) {
		switch (position != null ? position.toLowerCase() : "") {
			case "top": return 1;
			case "jng": return 2;
			case "jungle": return 2;
			case "mid": return 3;
			case "bot": return 4;
			case "sup": return 5;
			case "support": return 5;
			default: return 99;
		}
	}

	private String normalizeLeague(String league) {
		if (league == null || league.isBlank()) {
			return "LCK";
		}
		return league.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeSide(String side) {
		if (side == null || side.isBlank() || "ALL".equalsIgnoreCase(side)) {
			return null;
		}
		return side.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeBlank(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String normalizePosition(String position) {
		if (position == null) {
			return null;
		}
		return switch (position.toLowerCase(Locale.ROOT)) {
			case "jng" -> "JUNGLE";
			case "sup" -> "SUPPORT";
			default -> position.toUpperCase(Locale.ROOT);
		};
	}

	private String formatTimestampToKstDate(Object timestamp) {
		if (timestamp == null) {
			return null;
		}
		LocalDateTime utcDateTime;
		if (timestamp instanceof Timestamp ts) {
			utcDateTime = ts.toLocalDateTime();
		} else if (timestamp instanceof LocalDateTime ldt) {
			utcDateTime = ldt;
		} else {
			return timestamp.toString();
		}
		return utcDateTime.atOffset(ZoneOffset.UTC)
				.atZoneSameInstant(KST)
				.toLocalDate()
				.format(DATE_FORMATTER);
	}

	private int toInt(Object value) {
		Object scalar = unwrapScalar(value);
		if (scalar == null) {
			return 0;
		}
		return ((Number) scalar).intValue();
	}

	private long toLong(Object value) {
		Object scalar = unwrapScalar(value);
		if (scalar == null) {
			return 0L;
		}
		return ((Number) scalar).longValue();
	}

	private double toDouble(Object value) {
		Object scalar = unwrapScalar(value);
		if (scalar == null) {
			return 0;
		}
		return ((Number) scalar).doubleValue();
	}

	private String toString(Object value) {
		Object scalar = unwrapScalar(value);
		return scalar == null ? null : scalar.toString();
	}

	private double round(double value, int scale) {
		double factor = Math.pow(10, scale);
		return Math.round(value * factor) / factor;
	}

	private Object[] firstRow(List<Object[]> rows) {
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		Object[] first = rows.get(0);
		// Hibernate/JPA provider can return nested tuple arrays in some native-query paths.
		if (first != null && first.length == 1 && first[0] instanceof Object[] nested) {
			return nested;
		}
		return first;
	}

	private Object unwrapScalar(Object value) {
		Object current = value;
		while (current instanceof Object[] nested && nested.length > 0) {
			current = nested[0];
		}
		return current;
	}
}
