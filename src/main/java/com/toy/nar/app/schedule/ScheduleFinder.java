package com.toy.nar.app.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.LeagueConstants;
import com.toy.nar.app.lolesports.MatchDateWindow;
import com.toy.nar.app.lolesports.MatchResultDto;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.schedule.dto.ScheduleItemDto;
import com.toy.nar.app.schedule.dto.ScheduleResponseDto;
import com.toy.nar.app.schedule.dto.MatchSummaryDto;
import com.toy.nar.app.schedule.dto.TeamResultDto;
import com.toy.nar.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleFinder {
	private static final String LOLESPORTS_SOURCE = "LOLESPORTS";


	private final GameRepository gameRepository;
	private final com.toy.nar.app.lolesports.repository.LeagueMatchRepository leagueMatchRepository;
	private final LeagueMatchGameRepository leagueMatchGameRepository;
	private final ObjectMapper objectMapper;

	// ScheduleService에 있던 내부 record들을 데이터와 가장 가까운 이곳으로 이동
	private record GameInfoForSummary(
			Long gameId,
			LocalDateTime scheduledGameStartTime,
			String leagueName,
			String seasonSplit,
			List<ParticipantInfo> participants) {
	}

	private record ParticipantInfo(String teamName, boolean isWin) {
	}

	public ScheduleResponseDto createScheduleResponseDto(LocalDate date) {
		// Use only LeagueMatch data (Lolesports) for the schedule list
		LocalDateTime startOfDayKst = date.atStartOfDay();
		LocalDateTime endOfDayKst = date.atTime(23, 59, 59);

		// 1. Fetch all LeagueMatch data
		//
		// league_match.match_date 는 UTC 로 저장된다(아래 시각 렌더도 UTC→KST 로 바꾼다).
		// 그래서 "KST 하루"의 경계를 UTC 로 옮겨서 조회해야 한다. 예전에는 KST 날짜를 그대로
		// 넣어서, 실제로 담기는 구간이 KST 09:00 ~ 다음날 08:59 였다 — LCK(17·19시)는 우연히
		// 맞아떨어져 안 드러났고, KST 00:00~08:59 에 열리는 LEC·LCS 경기만 하루 앞 페이지에
		// 붙어서 "어제 끝난 경기가 오늘 unstarted 로 보인다"로 나타났다.
		LocalDateTime startOfDayUtc = MatchDateWindow.startOfDay(date);
		LocalDateTime endOfDayUtc = MatchDateWindow.endOfDay(date);
		List<com.toy.nar.app.lolesports.repository.LeagueMatch> leagueMatches = leagueMatchRepository
				.findByDateRange(startOfDayUtc, endOfDayUtc).stream()
				.filter(match -> LeagueConstants.ALLOWED_LEAGUES.contains(match.getLeagueName().toUpperCase()))
				.toList();

		// 2. Fetch all Game data for the same day (plus/minus buffer for timezone diffs
		// if needed) to check sync status efficiently
		// We use a slightly wider range to ensure we catch everything
		//
		// game.actual_game_start_time 은 match_date 와 저장 규약이 달라 위 UTC 경계를 그대로
		// 쓰지 않는다. ±12시간 버퍼가 어긋남을 흡수하므로 기존 기준(KST 날짜)을 유지한다.
		LocalDateTime searchStart = startOfDayKst.minusHours(12);
		LocalDateTime searchEnd = endOfDayKst.plusHours(12);
		List<com.toy.nar.domain.game.entity.Game> dailyGames = gameRepository
				.findAllByActualGameStartTimeBetween(searchStart, searchEnd);
		Map<String, MatchSyncStatus> syncStatusByMatchId = loadMatchSyncStatusByMatchId(leagueMatches);

		// 3. Map matches
		List<MatchSummaryDto> allMatches = leagueMatches.stream()
				.map(match -> createMatchSummaryFromLeagueMatch(match, dailyGames, syncStatusByMatchId.get(match.getId())))
				.sorted(Comparator.comparing(MatchSummaryDto::scheduledTime))
				.toList();

		return new ScheduleResponseDto(date.toString(), allMatches);
	}

	private MatchSummaryDto createMatchSummaryFromLeagueMatch(
			com.toy.nar.app.lolesports.repository.LeagueMatch leagueMatch,
			List<com.toy.nar.domain.game.entity.Game> dailyGames,
			MatchSyncStatus mappedStatus) {
		String normalizedBlueTeamName = com.toy.nar.common.util.NameNormalizer
				.normalizeTeamName(leagueMatch.getBlueTeamName());
		String normalizedRedTeamName = com.toy.nar.common.util.NameNormalizer
				.normalizeTeamName(leagueMatch.getRedTeamName());

		String scheduledTime = leagueMatch.getMatchDate() != null
				? leagueMatch.getMatchDate().atZone(MatchDateWindow.MATCH_DATE_ZONE)
						.withZoneSameInstant(MatchDateWindow.KST)
						.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
				: "";

		TeamResultDto teamA = new TeamResultDto(
				normalizedBlueTeamName,
				leagueMatch.getBlueTeamCode(),
				leagueMatch.getBlueTeamImageUrl(),
				leagueMatch.getBlueScore() != null ? leagueMatch.getBlueScore() : 0);
		TeamResultDto teamB = new TeamResultDto(
				normalizedRedTeamName,
				leagueMatch.getRedTeamCode(),
				leagueMatch.getRedTeamImageUrl(),
				leagueMatch.getRedScore() != null ? leagueMatch.getRedScore() : 0);

		// Check if any game in dailyGames matches this LeagueMatch
		// Matching logic: Same date (approx) + Same Teams (normalized)
		// Since we don't have exact ID link, we use heuristic similar to
		// ScheduleService
		boolean isSynced = mappedStatus != null && mappedStatus.hasTrackedRows()
				? mappedStatus.isSynced()
				: dailyGames.stream().anyMatch(game -> {
			// Check Team Names first (fastest)
			// Need to normalize game team names too? Or assume they are correct in DB?
			// Game team names in DB are usually already normalized or standard.
			// However, let's normalize to be safe.
			// Check Team Names from participants
			String gameBlue = getTeamNameFromGame(game, "Blue");
			String gameRed = getTeamNameFromGame(game, "Red");

			// Normalize
			gameBlue = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(gameBlue);
			gameRed = com.toy.nar.common.util.NameNormalizer.normalizeTeamName(gameRed);

			boolean teamMatch = (gameBlue.equalsIgnoreCase(normalizedBlueTeamName)
					&& gameRed.equalsIgnoreCase(normalizedRedTeamName))
					|| (gameBlue.equalsIgnoreCase(normalizedRedTeamName)
							&& gameRed.equalsIgnoreCase(normalizedBlueTeamName)); // Swap case? unlikely but possible
																					// side swap in data?

			if (!teamMatch)
				return false;

			// Check Date
			// LeagueMatch date is UTC. Game actualStartTime is UTC.
			// Allow some buffer (e.g. within 24 hours? usually matches are unique per day
			// per team pair)
			// Let's use LocalDate checking.
			LocalDate leagueMatchDate = leagueMatch.getMatchDate().toLocalDate(); // UTC date
			LocalDate gameDate = game.getActualGameStartTime().toLocalDate(); // UTC date

			// Simple date equality (in UTC)
			return leagueMatchDate.equals(gameDate);
		});

		String liveStreamUrl = null;
		if ("inProgress".equalsIgnoreCase(leagueMatch.getState())) {
			liveStreamUrl = LeagueConstants.getLiveStreamUrl(leagueMatch.getLeagueName());
		}

		// Parse VOD sets from matchDetailsJson
		List<MatchSummaryDto.SetVodDto> sets = parseSetVods(leagueMatch.getMatchDetailsJson());

		return MatchSummaryDto.builder()
				.matchId(leagueMatch.getId())
				.scheduledTime(scheduledTime)
				.leagueInfo(leagueMatch.getLeagueName())
				.matchTitle(leagueMatch.getMatchTitle())
				.matchStatus(leagueMatch.getState())
				.isSynced(isSynced)
				.teamA(teamA)
				.teamB(teamB)
				.liveStreamUrl(liveStreamUrl)
				.sets(sets)
				.build();
	}

	private List<MatchSummaryDto.SetVodDto> parseSetVods(String matchDetailsJson) {
		if (matchDetailsJson == null || matchDetailsJson.isBlank()) {
			return Collections.emptyList();
		}
		try {
			List<MatchResultDto.SetVod> setVods = objectMapper.readValue(
					matchDetailsJson,
					new TypeReference<List<MatchResultDto.SetVod>>() {
					});
			return setVods.stream()
					.map(sv -> new MatchSummaryDto.SetVodDto(sv.getSetNumber(), sv.getVodUrl()))
					.toList();
		} catch (JsonProcessingException e) {
			log.warn("Failed to parse matchDetailsJson: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	private String getTeamNameFromGame(com.toy.nar.domain.game.entity.Game game, String side) {
		return game.getParticipants().stream()
				.filter(p -> p.getSide().equalsIgnoreCase(side))
				.findFirst()
				.map(p -> p.getTeam().getName())
				.orElse("");
	}

	private Map<String, MatchSyncStatus> loadMatchSyncStatusByMatchId(
			List<com.toy.nar.app.lolesports.repository.LeagueMatch> leagueMatches) {
		List<String> matchIds = leagueMatches.stream()
				.map(com.toy.nar.app.lolesports.repository.LeagueMatch::getId)
				.filter(Objects::nonNull)
				.toList();
		if (matchIds.isEmpty()) {
			return Map.of();
		}

		Map<String, List<LeagueMatchGameRepository.MappedGameRow>> rowsByMatchId = leagueMatchGameRepository
				.findMappedGameRowsByMatchIds(matchIds, LOLESPORTS_SOURCE).stream()
				.collect(Collectors.groupingBy(
						LeagueMatchGameRepository.MappedGameRow::getMatchId,
						LinkedHashMap::new,
						Collectors.toList()));

		Map<String, MatchSyncStatus> statusByMatchId = new HashMap<>();
		for (com.toy.nar.app.lolesports.repository.LeagueMatch match : leagueMatches) {
			List<LeagueMatchGameRepository.MappedGameRow> trackedRows = rowsByMatchId
					.getOrDefault(match.getId(), List.of()).stream()
					.filter(row -> shouldTrackGameRow(match, row.getGameOrder()))
					.toList();

			if (trackedRows.isEmpty()) {
				statusByMatchId.put(match.getId(), new MatchSyncStatus(false, false));
				continue;
			}

			boolean anyMapped = trackedRows.stream().anyMatch(row -> row.getInternalGameId() != null);
			boolean allMapped = trackedRows.stream().allMatch(row -> row.getInternalGameId() != null);
			statusByMatchId.put(match.getId(), new MatchSyncStatus(true, anyMapped && allMapped));
		}
		return statusByMatchId;
	}

	private boolean shouldTrackGameRow(
			com.toy.nar.app.lolesports.repository.LeagueMatch leagueMatch,
			Integer gameOrder) {
		if (gameOrder == null) {
			return false;
		}
		if ("unstarted".equalsIgnoreCase(leagueMatch.getState())) {
			return false;
		}
		if (!"completed".equalsIgnoreCase(leagueMatch.getState())) {
			return true;
		}
		if (leagueMatch.getBlueScore() == null || leagueMatch.getRedScore() == null) {
			return true;
		}
		int playedSets = leagueMatch.getBlueScore() + leagueMatch.getRedScore();
		return playedSets <= 0 || gameOrder <= playedSets;
	}

	private String encodeMatchId(Set<Long> gameIds) {
		String idString = gameIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
		return Base64.getEncoder().encodeToString(idString.getBytes());
	}

	private record MatchSyncStatus(boolean hasTrackedRows, boolean isSynced) {
	}
}
