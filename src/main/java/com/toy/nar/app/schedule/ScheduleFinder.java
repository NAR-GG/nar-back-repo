package com.toy.nar.app.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.LeagueConstants;
import com.toy.nar.app.lolesports.MatchResultDto;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleFinder {

	private final GameRepository gameRepository;
	private final com.toy.nar.app.lolesports.repository.LeagueMatchRepository leagueMatchRepository;
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
		List<com.toy.nar.app.lolesports.repository.LeagueMatch> leagueMatches = leagueMatchRepository
				.findByDateRange(startOfDayKst, endOfDayKst).stream()
				.filter(match -> LeagueConstants.ALLOWED_LEAGUES.contains(match.getLeagueName().toUpperCase()))
				.toList();

		// 2. Fetch all Game data for the same day (plus/minus buffer for timezone diffs
		// if needed) to check sync status efficiently
		// We use a slightly wider range to ensure we catch everything
		LocalDateTime searchStart = startOfDayKst.minusHours(12);
		LocalDateTime searchEnd = endOfDayKst.plusHours(12);
		List<com.toy.nar.domain.game.entity.Game> dailyGames = gameRepository
				.findAllByActualGameStartTimeBetween(searchStart, searchEnd);

		// 3. Map matches
		List<MatchSummaryDto> allMatches = leagueMatches.stream()
				.map(match -> createMatchSummaryFromLeagueMatch(match, dailyGames))
				.sorted(Comparator.comparing(MatchSummaryDto::scheduledTime))
				.toList();

		return new ScheduleResponseDto(date.toString(), allMatches);
	}

	private MatchSummaryDto createMatchSummaryFromLeagueMatch(
			com.toy.nar.app.lolesports.repository.LeagueMatch leagueMatch,
			List<com.toy.nar.domain.game.entity.Game> dailyGames) {
		String normalizedBlueTeamName = com.toy.nar.common.util.NameNormalizer
				.normalizeTeamName(leagueMatch.getBlueTeamName());
		String normalizedRedTeamName = com.toy.nar.common.util.NameNormalizer
				.normalizeTeamName(leagueMatch.getRedTeamName());

		String scheduledTime = leagueMatch.getMatchDate() != null
				? leagueMatch.getMatchDate().atZone(ZoneId.of("UTC"))
						.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
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
		boolean isSynced = dailyGames.stream().anyMatch(game -> {
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

	private String encodeMatchId(Set<Long> gameIds) {
		String idString = gameIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
		return Base64.getEncoder().encodeToString(idString.getBytes());
	}
}