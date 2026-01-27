package com.toy.nar.app.schedule;

import com.toy.nar.app.schedule.dto.ScheduleItemDto;
import com.toy.nar.app.schedule.dto.ScheduleResponseDto;
import com.toy.nar.app.schedule.dto.MatchSummaryDto;
import com.toy.nar.app.schedule.dto.TeamResultDto;
import com.toy.nar.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleFinder {

	private final GameRepository gameRepository;
	private final com.toy.nar.app.lolesports.repository.LeagueMatchRepository leagueMatchRepository;

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

	private static final Set<String> ALLOWED_LEAGUES = Set.of("LCK", "LPL", "LCP", "LEC", "LCS", "CBLOL", "MSI",
			"WORLDS");

	public ScheduleResponseDto createScheduleResponseDto(LocalDate date) {
		// Use only LeagueMatch data (Lolesports) for the schedule list
		LocalDateTime startOfDayKst = date.atStartOfDay();
		LocalDateTime endOfDayKst = date.atTime(23, 59, 59);

		// 1. Fetch all LeagueMatch data
		List<com.toy.nar.app.lolesports.repository.LeagueMatch> leagueMatches = leagueMatchRepository
				.findByDateRange(startOfDayKst, endOfDayKst).stream()
				.filter(match -> ALLOWED_LEAGUES.contains(match.getLeagueName().toUpperCase()))
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

		TeamResultDto teamA = new TeamResultDto(normalizedBlueTeamName,
				leagueMatch.getBlueScore() != null ? leagueMatch.getBlueScore() : 0);
		TeamResultDto teamB = new TeamResultDto(normalizedRedTeamName,
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

		return MatchSummaryDto.builder()
				.matchId(leagueMatch.getId())
				.scheduledTime(scheduledTime)
				.leagueInfo(leagueMatch.getLeagueName())
				.matchStatus(leagueMatch.getState())
				.isSynced(isSynced)
				.teamA(teamA)
				.teamB(teamB)
				.build();
	}

	private String getTeamNameFromGame(com.toy.nar.domain.game.entity.Game game, String side) {
		return game.getParticipants().stream()
				.filter(p -> p.getSide().equalsIgnoreCase(side))
				.findFirst()
				.map(p -> p.getTeam().getName())
				.orElse("");
	}

	private MatchSummaryDto createMatchSummaryFromScheduleItems(List<List<ScheduleItemDto>> matchGames) {
		List<GameInfoForSummary> gamesForSummary = matchGames.stream()
				.map(gameParticipants -> {
					ScheduleItemDto representative = gameParticipants.get(0);
					List<ParticipantInfo> participantInfos = gameParticipants.stream()
							.map(p -> new ParticipantInfo(p.teamName(), p.isWin()))
							.toList();
					return new GameInfoForSummary(
							representative.gameId(),
							representative.scheduledGameStartTime(),
							representative.leagueName(),
							representative.seasonSplit(),
							participantInfos);
				})
				.toList();
		return createMatchSummary(gamesForSummary);
	}

	private MatchSummaryDto createMatchSummary(List<GameInfoForSummary> matchGames) {
		if (matchGames.isEmpty() || matchGames.get(0).participants().isEmpty()) {
			return null;
		}
		GameInfoForSummary firstGame = matchGames.get(0);

		List<String> sortedTeamNames = firstGame.participants().stream()
				.map(ParticipantInfo::teamName).distinct().sorted().toList();
		if (sortedTeamNames.size() < 2) {
			return null;
		}
		String teamAName = sortedTeamNames.get(0);
		String teamBName = sortedTeamNames.get(1);

		int teamAScore = 0;
		int teamBScore = 0;
		for (GameInfoForSummary game : matchGames) {
			String winnerTeam = game.participants().stream()
					.filter(ParticipantInfo::isWin)
					.map(ParticipantInfo::teamName)
					.findFirst().orElse("");
			if (winnerTeam.equals(teamAName))
				teamAScore++;
			else if (winnerTeam.equals(teamBName))
				teamBScore++;
		}

		TeamResultDto teamA = new TeamResultDto(teamAName, teamAScore);
		TeamResultDto teamB = new TeamResultDto(teamBName, teamBScore);

		String matchId = encodeMatchId(matchGames.stream().map(GameInfoForSummary::gameId).collect(Collectors.toSet()));
		String leagueInfo = String.format("%s %s", firstGame.leagueName(), firstGame.seasonSplit());
		String scheduledTime = firstGame.scheduledGameStartTime()
				.atZone(ZoneId.of("UTC"))
				.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
				.format(DateTimeFormatter.ofPattern("HH:mm"));

		return MatchSummaryDto.builder()
				.matchId(matchId)
				.scheduledTime(scheduledTime)
				.leagueInfo(leagueInfo)
				.matchStatus("completed")
				.isSynced(true)
				.teamA(teamA)
				.teamB(teamB)
				.build();
	}

	private String encodeMatchId(Set<Long> gameIds) {
		String idString = gameIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
		return Base64.getEncoder().encodeToString(idString.getBytes());
	}
}