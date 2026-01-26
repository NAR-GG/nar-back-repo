package com.toy.nar.app.schedule;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.toy.nar.app.schedule.dto.GameInfoForSummary;
import com.toy.nar.app.schedule.dto.GameInfoForSummary.ParticipantInfo;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto;
import com.toy.nar.app.schedule.dto.MatchSummaryDto;
import com.toy.nar.app.schedule.dto.TeamResultDto;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchDetailFinder {

	private final GameParticipantRepository gameParticipantRepository;

	public MatchDetailResponseDto findMatchDetail(String matchId) {
		Set<Long> gameIds = decodeMatchId(matchId);
		if (gameIds.isEmpty())
			return null;

		List<GameParticipant> participants = gameParticipantRepository.findGameDetailsByGameIds(gameIds);
		return convertToMatchDetailDto(participants, matchId);
	}

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
							participantInfos);
				})
				.toList();

		MatchSummaryDto summary = createMatchSummary(gamesForSummary);

		Map<Long, List<GameParticipant>> participantsByGame = participants.stream()
				.collect(Collectors.groupingBy(p -> p.getGame().getId()));

		List<MatchDetailResponseDto.GameDetailDto> gameDetails = participantsByGame.values().stream()
				.map(gameParticipants -> {
					Game game = gameParticipants.get(0).getGame();
					Map<String, List<GameParticipant>> participantsBySide = gameParticipants.stream()
							.collect(Collectors.groupingBy(GameParticipant::getSide));

					MatchDetailResponseDto.GameDetailDto.TeamPicksDto blueTeam = createTeamPicksDto(
							participantsBySide.get("Blue"));
					MatchDetailResponseDto.GameDetailDto.TeamPicksDto redTeam = createTeamPicksDto(
							participantsBySide.get("Red"));

					return new MatchDetailResponseDto.GameDetailDto(game.getId(), game.getGameNumber(),
							game.getGameLengthSeconds(), blueTeam, redTeam);
				})
				.sorted(Comparator.comparing(MatchDetailResponseDto.GameDetailDto::gameNumber))
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

		return new MatchSummaryDto(matchId, scheduledTime, leagueInfo, teamA, teamB);
	}

	private MatchDetailResponseDto.GameDetailDto.TeamPicksDto createTeamPicksDto(
			List<GameParticipant> teamParticipants) {
		if (teamParticipants == null || teamParticipants.isEmpty()) {
			return new MatchDetailResponseDto.GameDetailDto.TeamPicksDto("Unknown", false, Collections.emptyList());
		}
		String teamName = teamParticipants.get(0).getTeam().getName();
		boolean isWin = teamParticipants.get(0).getIsWin();

		List<MatchDetailResponseDto.GameDetailDto.PlayerPickDto> players = teamParticipants.stream()
				.map(p -> new MatchDetailResponseDto.GameDetailDto.PlayerPickDto(p.getPosition(),
						p.getPlayer().getName(), p.getChampion().getChampionNameEn()))
				.sorted(Comparator.comparing(p -> getPositionOrder(p.position())))
				.toList();

		return new MatchDetailResponseDto.GameDetailDto.TeamPicksDto(teamName, isWin, players);
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
