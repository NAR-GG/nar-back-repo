package com.toy.nar.app.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.schedule.dto.MatchDetailResponseDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto.PlayerPickDto;
import com.toy.nar.app.schedule.dto.MatchDetailResponseDto.GameDetailDto.TeamPicksDto;
import com.toy.nar.app.schedule.dto.MatchSummaryDto;
import com.toy.nar.app.schedule.dto.ScheduleResponseDto;
import com.toy.nar.app.schedule.dto.TeamResultDto;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;
import com.toy.nar.domain.game.repository.GameParticipantRepository;
import com.toy.nar.domain.game.repository.GameRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleService {

	private final GameRepository gameRepository;
	private final GameParticipantRepository gameParticipantRepository;

	/**
	 * 일정 목록 조회 서비스
	 */
	public ScheduleResponseDto getDailySchedule(LocalDate date) {
		// 1. KST 날짜 -> UTC 시간 범위로 변환
		LocalDateTime startOfDayUtc = date.atStartOfDay(ZoneId.of("Asia/Seoul")).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
		LocalDateTime endOfDayUtc = startOfDayUtc.plusDays(1);

		// 2. DB에서 해당 날짜의 모든 게임 상세 정보 조회
		List<Game> games = gameRepository.findGamesByScheduledDateWithDetails(startOfDayUtc, endOfDayUtc);

		// 3. 게임(세트)들을 '매치' 단위로 묶어서 DTO로 변환
		Map<Set<String>, List<Game>> gamesByMatchup = games.stream()
			.collect(Collectors.groupingBy(game ->
				game.getParticipants().stream()
					.map(p -> p.getTeam().getName())
					.collect(Collectors.toSet())
			));

		List<MatchSummaryDto> matches = gamesByMatchup.values().stream()
			.map(this::createMatchSummaryDto)
			.sorted(Comparator.comparing(MatchSummaryDto::scheduledTime))
			.toList();

		return new ScheduleResponseDto(date.toString(), matches);
	}

	/**
	 * 매치 상세 정보 조회 서비스
	 */
	@Transactional(readOnly = true)
	public MatchDetailResponseDto getMatchDetail(String matchId) {
		// 1. matchId로부터 게임 ID 목록을 디코딩
		Set<Long> gameIds = decodeMatchId(matchId);

		// 2. 해당 게임들의 상세 정보 조회 (기존 메서드 활용)
		List<GameParticipant> participants = gameParticipantRepository.findGameDetailsByGameIds(gameIds);

		// 3. 상세 정보 DTO로 변환
		// (코드가 길어지므로 실제로는 별도의 Converter 클래스로 분리하는 것을 추천)
		return convertToMatchDetailDto(participants, matchId);
	}


	// --- Private Helper Methods ---

	private MatchSummaryDto createMatchSummaryDto(List<Game> matchGames) {
		Game firstGame = matchGames.get(0);

		// 1. 팀별 승리 횟수 계산
		Map<String, Long> winsByTeam = matchGames.stream()
			.flatMap(g -> g.getParticipants().stream())
			.filter(GameParticipant::getIsWin)
			.map(p -> p.getTeam().getName())
			.collect(Collectors.groupingBy(teamName -> teamName, Collectors.counting()));

		// 2. 두 팀의 정보로 TeamResultDto 생성
		List<TeamResultDto> teams = firstGame.getParticipants().stream()
			.map(p -> p.getTeam().getName())
			.distinct()
			.map(teamName -> new TeamResultDto(teamName, winsByTeam.getOrDefault(teamName, 0L).intValue()/5))
			.sorted(Comparator.comparing(TeamResultDto::teamName)) // 팀 이름 순으로 정렬하여 A, B팀 순서 고정
			.toList();

		// 3. matchId 생성
		String matchId = encodeMatchId(matchGames.stream().map(Game::getId).collect(Collectors.toSet()));

		// 4. 최종 DTO 조립
		String leagueInfo = String.format("%s %s", firstGame.getLeague().getLeagueName(), firstGame.getLeague().getSeasonSplit());
		String scheduledTime = firstGame.getScheduledGameStartTime()
			.atZone(ZoneId.of("UTC"))
			.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
			.format(DateTimeFormatter.ofPattern("HH:mm"));

		return new MatchSummaryDto(matchId, scheduledTime, leagueInfo, teams.get(0), teams.get(1));
	}

	private MatchDetailResponseDto convertToMatchDetailDto(List<GameParticipant> participants, String matchId) {
		// 1. 재사용을 위해 요약 정보부터 생성
		List<Game> gamesForSummary = participants.stream().map(GameParticipant::getGame).distinct().toList();
		MatchSummaryDto summary = createMatchSummaryDto(gamesForSummary);

		// 2. 게임(세트) ID 별로 참가자들 그룹화
		Map<Long, List<GameParticipant>> participantsByGame = participants.stream()
			.collect(Collectors.groupingBy(p -> p.getGame().getId()));

		// 3. 각 게임(세트)을 GameDetailDto로 변환
		List<GameDetailDto> gameDetails = participantsByGame.values().stream()
			.map(gameParticipants -> {
				Game game = gameParticipants.get(0).getGame();

				// 3-1. Blue/Red 팀으로 참가자 그룹화
				Map<String, List<GameParticipant>> participantsBySide = gameParticipants.stream()
					.collect(Collectors.groupingBy(GameParticipant::getSide));

				// 3-2. 각 팀의 픽 정보를 TeamPicksDto로 변환
				TeamPicksDto blueTeam = createTeamPicksDto(participantsBySide.get("Blue"));
				TeamPicksDto redTeam = createTeamPicksDto(participantsBySide.get("Red"));

				return new GameDetailDto(game.getId(), game.getGameNumber(), game.getGameLengthSeconds(), blueTeam, redTeam);
			})
			.sorted(Comparator.comparing(GameDetailDto::gameNumber)) // gameNumber 순으로 정렬
			.toList();

		return new MatchDetailResponseDto(summary, gameDetails);
	}

	private TeamPicksDto createTeamPicksDto(List<GameParticipant> teamParticipants) {
		String teamName = teamParticipants.get(0).getTeam().getName();
		boolean isWin = teamParticipants.get(0).getIsWin();

		List<PlayerPickDto> players = teamParticipants.stream()
			.map(p -> new PlayerPickDto(p.getPosition(), p.getPlayer().getName(), p.getChampion().getChampionNameEn()))
			.sorted(Comparator.comparing(p -> getPositionOrder(p.position()))) // 포지션 순서대로 정렬
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
