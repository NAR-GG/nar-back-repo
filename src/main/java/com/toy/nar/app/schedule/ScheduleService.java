package com.toy.nar.app.schedule;

import com.toy.nar.app.schedule.dto.*;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto.PlayerPickDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto.TeamPicksDto;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
public class ScheduleService {

	private final GameRepository gameRepository;
	private final GameParticipantRepository gameParticipantRepository;

	// [리팩토링 1] 요약 정보 생성을 위한 최소 데이터 구조 정의 (inner record 사용)
	private record GameInfoForSummary(
		Long gameId,
		LocalDateTime scheduledGameStartTime,
		String leagueName,
		String seasonSplit,
		List<ParticipantInfo> participants
	) {}

	private record ParticipantInfo(String teamName, boolean isWin) {}


	/**
	 * 일정 목록 조회 서비스 (캐싱 적용)
	 */
	@Cacheable(value = "dailySchedules", key = "#date.toString()")
	public ScheduleResponseDto getDailySchedule(LocalDate date) {
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
			.map(matchGames -> {
				// [리팩토링 2] DB 조회 결과(DTO)를 공통 데이터 구조로 변환
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
				// [리팩토링 3] 통합된 단일 메서드 호출
				return createMatchSummary(gamesForSummary);
			})
			.sorted(Comparator.comparing(MatchSummaryDto::scheduledTime))
			.toList();

		return new ScheduleResponseDto(date.toString(), matches);
	}

	/**
	 * 매치 상세 정보 조회 서비스
	 */
	@Transactional(readOnly = true)
	public MatchDetailResponseDto getMatchDetail(String matchId) {
		Set<Long> gameIds = decodeMatchId(matchId);
		List<GameParticipant> participants = gameParticipantRepository.findGameDetailsByGameIds(gameIds);
		return convertToMatchDetailDto(participants, matchId);
	}


	// --- Private Helper Methods ---

	/**
	 * [리팩토링 4] 통합된 매치 요약 DTO 생성 메서드
	 * 어떤 데이터 소스에서 왔는지와 무관하게, GameInfoForSummary 리스트만 받아서 MatchSummaryDto를 생성하는 단일 책임
	 */
	private MatchSummaryDto createMatchSummary(List<GameInfoForSummary> matchGames) {
		GameInfoForSummary firstGame = matchGames.get(0);

		// 팀 이름 정렬 (TeamA, TeamB 순서 고정 위함)
		List<String> sortedTeamNames = firstGame.participants().stream()
			.map(ParticipantInfo::teamName).distinct().sorted().toList();
		String teamAName = sortedTeamNames.get(0);
		String teamBName = sortedTeamNames.get(1);

		// 팀별 승리 횟수(스코어) 계산
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

	private MatchDetailResponseDto convertToMatchDetailDto(List<GameParticipant> participants, String matchId) {
		// [리팩토링 5] Game 엔티티를 공통 데이터 구조로 변환하여 재사용
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

	// createTeamPicksDto, encodeMatchId, decodeMatchId, getPositionOrder 메서드는 변경 없음
	private TeamPicksDto createTeamPicksDto(List<GameParticipant> teamParticipants) {
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