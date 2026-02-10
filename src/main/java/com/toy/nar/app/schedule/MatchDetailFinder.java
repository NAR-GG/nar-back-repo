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
		List<Game> games = participants.stream().map(GameParticipant::getGame).distinct()
				.sorted(Comparator.comparingInt(Game::getGameNumber)).toList();

		List<GameInfoForSummary> gamesForSummary = games.stream()
				.map(game -> {
					List<ParticipantInfo> participantInfos = game.getParticipants().stream()
							.map(p -> new ParticipantInfo(
									p.getTeam().getName(),
									p.getTeam().getCode(),
									p.getTeam().getImageUrl(),
									p.getIsWin()))
							.toList();
					return new GameInfoForSummary(
							game.getId(),
							game.getScheduledGameStartTime() != null ? game.getScheduledGameStartTime()
									: game.getActualGameStartTime(),
							game.getLeague().getLeagueName(),
							game.getLeague().getSeasonSplit(),
							participantInfos);
				})
				.toList();

		MatchSummaryDto summary = createMatchSummary(gamesForSummary);

		List<MatchDetailResponseDto.GameDetailDto> gameDetails = createGameDetails(participants, null);

		return new MatchDetailResponseDto(summary, gameDetails);
	}

	public List<MatchDetailResponseDto.GameDetailDto> createGameDetails(List<GameParticipant> participants,
			Map<Integer, String> vodMap) {
		List<Game> games = participants.stream().map(GameParticipant::getGame).distinct()
				.sorted(Comparator.comparingInt(Game::getGameNumber)).toList();

		Map<Long, List<GameParticipant>> participantsByGame = participants.stream()
				.collect(Collectors.groupingBy(p -> p.getGame().getId()));

		// Fearless Draft Logic: Accumulate picks from previous games
		Map<String, Set<String>> teamAccumulatedPicks = new HashMap<>();

		List<MatchDetailResponseDto.GameDetailDto> gameDetails = new ArrayList<>();
		for (Game game : games) {
			List<GameParticipant> gameParticipants = participantsByGame.get(game.getId());
			Map<String, List<GameParticipant>> participantsBySide = gameParticipants.stream()
					.collect(Collectors.groupingBy(GameParticipant::getSide));

			List<GameParticipant> blueSide = participantsBySide.get("Blue");
			List<GameParticipant> redSide = participantsBySide.get("Red");

			// Get team names
			String blueTeamName = blueSide != null && !blueSide.isEmpty() ? blueSide.get(0).getTeam().getName() : "";
			String redTeamName = redSide != null && !redSide.isEmpty() ? redSide.get(0).getTeam().getName() : "";

			// Initialize accumulated picks if not present
			teamAccumulatedPicks.putIfAbsent(blueTeamName, new HashSet<>());
			teamAccumulatedPicks.putIfAbsent(redTeamName, new HashSet<>());

			// Get current game bans
			Set<String> blueTeamBans = game.getBans().stream()
					.filter(b -> b.getTeam().getName().equals(blueTeamName))
					.map(b -> b.getBannedChampion().getChampionNameEn())
					.collect(Collectors.toSet());

			Set<String> redTeamBans = game.getBans().stream()
					.filter(b -> b.getTeam().getName().equals(redTeamName))
					.map(b -> b.getBannedChampion().getChampionNameEn())
					.collect(Collectors.toSet());

			// Add accumulated picks to bans (Fearless Draft)
			blueTeamBans.addAll(teamAccumulatedPicks.get(blueTeamName));
			redTeamBans.addAll(teamAccumulatedPicks.get(redTeamName));

			// Create DTOs with accumulated bans
			MatchDetailResponseDto.GameDetailDto.TeamPicksDto blueTeamDto = createTeamPicksDto(blueSide,
					new ArrayList<>(blueTeamBans));
			MatchDetailResponseDto.GameDetailDto.TeamPicksDto redTeamDto = createTeamPicksDto(redSide,
					new ArrayList<>(redTeamBans));

			String vodUrl = vodMap != null ? vodMap.get(game.getGameNumber()) : null;

			gameDetails.add(new MatchDetailResponseDto.GameDetailDto(game.getId(), game.getGameNumber(),
					game.getGameLengthSeconds(), vodUrl, blueTeamDto, redTeamDto));

			// Update accumulated picks for next games
			if (blueSide != null) {
				blueSide.forEach(
						p -> teamAccumulatedPicks.get(blueTeamName).add(p.getChampion().getChampionNameEn()));
			}
			if (redSide != null) {
				redSide.forEach(p -> teamAccumulatedPicks.get(redTeamName).add(p.getChampion().getChampionNameEn()));
			}
		}
		return gameDetails;
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

		// Find metadata for teams
		String teamACode = firstGame.participants().stream()
				.filter(p -> p.teamName().equals(teamAName))
				.findFirst().map(ParticipantInfo::teamCode).orElse(null);
		String teamAImage = firstGame.participants().stream()
				.filter(p -> p.teamName().equals(teamAName))
				.findFirst().map(ParticipantInfo::teamImageUrl).orElse(null);

		String teamBCode = firstGame.participants().stream()
				.filter(p -> p.teamName().equals(teamBName))
				.findFirst().map(ParticipantInfo::teamCode).orElse(null);
		String teamBImage = firstGame.participants().stream()
				.filter(p -> p.teamName().equals(teamBName))
				.findFirst().map(ParticipantInfo::teamImageUrl).orElse(null);

		TeamResultDto teamA = new TeamResultDto(teamAName, teamACode, teamAImage, teamAScore);
		TeamResultDto teamB = new TeamResultDto(teamBName, teamBCode, teamBImage, teamBScore);

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
				.matchTitle(leagueInfo) // Internal items don't have separate title, reuse leagueInfo
				.matchStatus("completed")
				.isSynced(true)
				.teamA(teamA)
				.teamB(teamB)
				.build();
	}

	private MatchDetailResponseDto.GameDetailDto.TeamPicksDto createTeamPicksDto(
			List<GameParticipant> teamParticipants, List<String> bans) {
		if (teamParticipants == null || teamParticipants.isEmpty()) {
			return new MatchDetailResponseDto.GameDetailDto.TeamPicksDto("Unknown", false, Collections.emptyList(),
					Collections.emptyList());
		}
		String teamName = teamParticipants.get(0).getTeam().getName();
		boolean isWin = teamParticipants.get(0).getIsWin();

		// Ensure bans list is not null
		List<String> validBans = bans != null ? bans : Collections.emptyList();

		List<MatchDetailResponseDto.GameDetailDto.PlayerPickDto> players = teamParticipants.stream()
				.map(p -> new MatchDetailResponseDto.GameDetailDto.PlayerPickDto(p.getPosition(),
						p.getPlayer().getName(), p.getChampion().getChampionNameEn()))
				.sorted(Comparator.comparing(p -> getPositionOrder(p.position())))
				.toList();

		return new MatchDetailResponseDto.GameDetailDto.TeamPicksDto(teamName, isWin, validBans, players);
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
