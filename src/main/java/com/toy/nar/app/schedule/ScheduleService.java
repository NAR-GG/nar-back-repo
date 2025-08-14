package com.toy.nar.app.schedule;

import com.toy.nar.app.schedule.dto.*;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto.PlayerPickDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto.TeamPicksDto;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
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
public class ScheduleService {

	private final GameParticipantRepository gameParticipantRepository;
	private final ScheduleCacheableService scheduleCacheableService;

	private record GameInfoForSummary(
		Long gameId,
		LocalDateTime scheduledGameStartTime,
		String leagueName,
		String seasonSplit,
		List<ParticipantInfo> participants
	) {}

	private record ParticipantInfo(String teamName, boolean isWin) {}


	/**
	 * 일정 조회 공개 메서드.
	 * 날짜에 따라 오늘 또는 과거 일정을 조회하는 캐시 서비스를 호출합니다.
	 */
	public ScheduleResponseDto getDailySchedule(LocalDate date) {
		if (date.isEqual(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
			return scheduleCacheableService.getTodaySchedule(date);
		}
		return scheduleCacheableService.getPastSchedule(date);
	}

	/**
	 * 매치 상세 정보 조회 서비스
	 */
	@Cacheable(value = "matchDetails", key = "#matchId", unless = "#result == null or #matchId == null")
	@Transactional(readOnly = true)
	public MatchDetailResponseDto getMatchDetail(String matchId) {
		log.info("DB에서 매치 상세 정보를 조회합니다: matchId={}", matchId);
		Set<Long> gameIds = decodeMatchId(matchId);
		List<GameParticipant> participants = gameParticipantRepository.findGameDetailsByGameIds(gameIds);
		return convertToMatchDetailDto(participants, matchId);
	}


	// --- Private Helper Methods for MatchDetail ---

	private MatchDetailResponseDto convertToMatchDetailDto(List<GameParticipant> participants, String matchId) {
		if (participants.isEmpty()) {
			// 조회된 데이터가 없으면 null을 반환하여 캐시되지 않도록 합니다.
			return null;
		}
		List<Game> games = participants.stream().map(GameParticipant::getGame).distinct().toList();
		List<GameInfoForSummary> gamesForSummary = games.stream()
			.map(game -> {
				List<ParticipantInfo> participantInfos = game.getParticipants().stream()
					.map(p -> new ParticipantInfo(p.getTeam().getName(), p.getIsWin()))
					.toList();
				return new GameInfoForSummary(
					game.getId(),
					game.getScheduledGameStartTime(),
					game.getLeague().getLeagueName(),
					game.getLeague().getSeasonSplit(),
					participantInfos
				);
			})
			.toList();

		MatchSummaryDto summary = createMatchSummary(gamesForSummary);

		Map<Long, List<GameParticipant>> participantsByGame = participants.stream()
			.collect(Collectors.groupingBy(p -> p.getGame().getId()));

		List<GameDetailDto> gameDetails = participantsByGame.values().stream()
			.map(gameParticipants -> {
				Game game = gameParticipants.get(0).getGame();
				Map<String, List<GameParticipant>> participantsBySide = gameParticipants.stream()
					.collect(Collectors.groupingBy(GameParticipant::getSide));

				TeamPicksDto blueTeam = createTeamPicksDto(participantsBySide.get("Blue"));
				TeamPicksDto redTeam = createTeamPicksDto(participantsBySide.get("Red"));

				return new GameDetailDto(game.getId(), game.getGameNumber(), game.getGameLengthSeconds(), blueTeam, redTeam);
			})
			.sorted(Comparator.comparing(GameDetailDto::gameNumber))
			.toList();

		return new MatchDetailResponseDto(summary, gameDetails);
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

	private TeamPicksDto createTeamPicksDto(List<GameParticipant> teamParticipants) {
		if(teamParticipants == null || teamParticipants.isEmpty()) {
			return new TeamPicksDto("Unknown", false, Collections.emptyList());
		}
		String teamName = teamParticipants.get(0).getTeam().getName();
		boolean isWin = teamParticipants.get(0).getIsWin();

		List<PlayerPickDto> players = teamParticipants.stream()
			.map(p -> new PlayerPickDto(p.getPosition(), p.getPlayer().getName(), p.getChampion().getChampionNameEn()))
			.sorted(Comparator.comparing(p -> getPositionOrder(p.position())))
			.toList();

		return new TeamPicksDto(teamName, isWin, players);
	}

	private String encodeMatchId(Set<Long> gameIds) {
		String idString = gameIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
		return Base64.getEncoder().encodeToString(idString.getBytes());
	}

	private Set<Long> decodeMatchId(String matchId) {
		byte[] decodedBytes = Base64.getDecoder().decode(matchId);
		String[] idStrings = new String(decodedBytes).split(",");
		return Arrays.stream(idStrings).map(Long::parseLong).collect(Collectors.toSet());
	}

	private int getPositionOrder(String position) {
		return switch (position.toLowerCase()) {
			case "top" -> 1;
			case "jng", "jungle" -> 2;
			case "mid" -> 3;
			case "bot", "adc" -> 4;
			case "sup", "support" -> 5;
			default -> 99;
		};
	}
}