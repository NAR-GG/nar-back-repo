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

	// ScheduleService에 있던 내부 record들을 데이터와 가장 가까운 이곳으로 이동
	private record GameInfoForSummary(
		Long gameId,
		LocalDateTime scheduledGameStartTime,
		String leagueName,
		String seasonSplit,
		List<ParticipantInfo> participants
	) {}

	private record ParticipantInfo(String teamName, boolean isWin) {}

	public ScheduleResponseDto createScheduleResponseDto(LocalDate date) {
		LocalDateTime startOfDayUtc = date.atStartOfDay(ZoneId.of("Asia/Seoul")).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
		LocalDateTime endOfDayUtc = startOfDayUtc.plusDays(1);

		List<ScheduleItemDto> scheduleItems = gameRepository.findScheduleItemsByDate(startOfDayUtc, endOfDayUtc);
		if (scheduleItems.isEmpty()) {
			return new ScheduleResponseDto(date.toString(), Collections.emptyList());
		}

		Map<Long, List<ScheduleItemDto>> gamesMap = scheduleItems.stream()
			.collect(Collectors.groupingBy(ScheduleItemDto::gameId));

		Map<Set<String>, List<List<ScheduleItemDto>>> matchesMap = new HashMap<>();
		for (List<ScheduleItemDto> gameParticipants : gamesMap.values()) {
			Set<String> teamNames = gameParticipants.stream().map(ScheduleItemDto::teamName).collect(Collectors.toSet());
			if (teamNames.size() == 2) {
				matchesMap.computeIfAbsent(teamNames, k -> new ArrayList<>()).add(gameParticipants);
			}
		}

		List<MatchSummaryDto> matches = matchesMap.values().stream()
			.map(this::createMatchSummaryFromScheduleItems)
			.filter(Objects::nonNull) // Handle cases where a summary might not be created
			.sorted(Comparator.comparing(MatchSummaryDto::scheduledTime))
			.toList();

		return new ScheduleResponseDto(date.toString(), matches);
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
					participantInfos
				);
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
			if (winnerTeam.equals(teamAName)) teamAScore++;
			else if (winnerTeam.equals(teamBName)) teamBScore++;
		}

		TeamResultDto teamA = new TeamResultDto(teamAName, teamAScore);
		TeamResultDto teamB = new TeamResultDto(teamBName, teamBScore);

		String matchId = encodeMatchId(matchGames.stream().map(GameInfoForSummary::gameId).collect(Collectors.toSet()));
		String leagueInfo = String.format("%s %s", firstGame.leagueName(), firstGame.seasonSplit());
		String scheduledTime = firstGame.scheduledGameStartTime()
			.atZone(ZoneId.of("UTC"))
			.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
			.format(DateTimeFormatter.ofPattern("HH:mm"));

		return new MatchSummaryDto(matchId, scheduledTime, leagueInfo, teamA, teamB);
	}

	private String encodeMatchId(Set<Long> gameIds) {
		String idString = gameIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
		return Base64.getEncoder().encodeToString(idString.getBytes());
	}
}