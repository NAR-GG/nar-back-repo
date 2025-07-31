package com.toy.nar.app.analysis.converter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.toy.nar.app.analysis.dto.CombinationDetailDto;
import com.toy.nar.domain.game.entity.Game;
import com.toy.nar.domain.game.entity.GameParticipant;

@Component
public class GameDetailConverter {

	public List<CombinationDetailDto.GameDetailDto> convertToGameDetails(
		List<GameParticipant> participants,
		String targetTeamName,
		List<String> targetChampions) {

		return participants.stream()
			.collect(Collectors.groupingBy(p -> p.getGame().getId()))
			.values().stream()
			.map(gameParticipants -> createGameDetail(gameParticipants, targetTeamName, targetChampions))
			.collect(Collectors.toList());
	}

	private CombinationDetailDto.GameDetailDto createGameDetail(
		List<GameParticipant> gameParticipants,
		String targetTeamName,
		List<String> targetChampions) {

		GameParticipant first = gameParticipants.get(0);
		Game game = first.getGame();

		// 팀별로 그룹화
		Map<String, List<GameParticipant>> teamGroups = gameParticipants.stream()
			.collect(Collectors.groupingBy(p -> p.getTeam().getName()));

		// 우리 팀과 상대 팀 분리 로직 개선
		CombinationDetailDto.TeamDetailDto ourTeam = null;
		CombinationDetailDto.TeamDetailDto opponentTeam = null;

		// 먼저 우리 팀을 찾기
		for (Map.Entry<String, List<GameParticipant>> entry : teamGroups.entrySet()) {
			String teamName = entry.getKey();
			List<GameParticipant> teamPlayers = entry.getValue();

			if (teamPlayers.size() == 5) {
				if (isOurTeam(teamName, targetTeamName, teamPlayers, targetChampions)) {
					ourTeam = createTeamDetail(teamName, teamPlayers);
					break; // 우리 팀을 찾으면 바로 중단
				}
			}
		}

		// 상대 팀 찾기 (우리 팀이 아닌 팀)
		for (Map.Entry<String, List<GameParticipant>> entry : teamGroups.entrySet()) {
			String teamName = entry.getKey();
			List<GameParticipant> teamPlayers = entry.getValue();

			if (teamPlayers.size() == 5) {
				if (ourTeam == null || !teamName.equals(ourTeam.teamName())) {
					opponentTeam = createTeamDetail(teamName, teamPlayers);
					break;
				}
			}
		}

		return new CombinationDetailDto.GameDetailDto(
			game.getId(),
			game.getActualGameStartTime(),
			game.getLeague().getSeasonSplit(),
			game.getLeague().getLeagueName(),
			game.getPatch(),
			game.getGameLengthSeconds(),
			ourTeam,
			opponentTeam,
			null
		);
	}

	// 우리 팀 판별 로직 개선
	private boolean isOurTeam(String teamName, String targetTeamName,
		List<GameParticipant> teamPlayers, List<String> targetChampions) {

		// 1. targetTeamName이 있으면 우선 사용
		if (targetTeamName != null && !targetTeamName.isEmpty()) {
			return teamName.equals(targetTeamName);
		}

		// 2. targetTeamName이 없으면 챔피언 조합으로 판별 (부분 일치)
		if (targetChampions != null && !targetChampions.isEmpty()) {
			List<String> teamChampions = teamPlayers.stream()
				.map(p -> p.getChampion().getChampionNameEn())
				.collect(Collectors.toList());

			// 부분 일치로 변경: 타겟 챔피언이 모두 팀에 포함되어 있는지 확인
			return teamChampions.containsAll(targetChampions);
		}

		// 3. 기본값: false (명시적으로 우리 팀이 아님)
		return false;
	}

	private CombinationDetailDto.TeamDetailDto createTeamDetail(String teamName, List<GameParticipant> teamPlayers) {
		GameParticipant first = teamPlayers.get(0);

		List<CombinationDetailDto.PlayerDetailDto> players = teamPlayers.stream()
			.map(p -> new CombinationDetailDto.PlayerDetailDto(
				p.getPosition(),
				p.getChampion().getChampionNameEn(),
				p.getPlayer().getName()
			))
			.sorted(Comparator.comparing(this::getPositionOrder))
			.collect(Collectors.toList());

		return new CombinationDetailDto.TeamDetailDto(
			teamName,
			first.getSide(),
			first.getIsWin(),
			players
		);
	}

	// Multi 버전 메서드들
	public List<CombinationDetailDto.GameDetailDto> convertToGameDetailsMulti(
		List<GameParticipant> participants,
		List<String> targetTeamNames,
		List<String> targetChampions) {

		return participants.stream()
			.collect(Collectors.groupingBy(p -> p.getGame().getId()))
			.values().stream()
			.map(gameParticipants -> createGameDetailMulti(gameParticipants, targetTeamNames, targetChampions))
			.collect(Collectors.toList());
	}

	// 누락된 createGameDetailMulti 메서드 추가
	private CombinationDetailDto.GameDetailDto createGameDetailMulti(
		List<GameParticipant> gameParticipants,
		List<String> targetTeamNames,
		List<String> targetChampions) {

		GameParticipant first = gameParticipants.get(0);
		Game game = first.getGame();

		// 팀별로 그룹화
		Map<String, List<GameParticipant>> teamGroups = gameParticipants.stream()
			.collect(Collectors.groupingBy(p -> p.getTeam().getName()));

		CombinationDetailDto.TeamDetailDto ourTeam = null;
		CombinationDetailDto.TeamDetailDto opponentTeam = null;

		// 먼저 우리 팀을 찾기
		for (Map.Entry<String, List<GameParticipant>> entry : teamGroups.entrySet()) {
			String teamName = entry.getKey();
			List<GameParticipant> teamPlayers = entry.getValue();

			if (teamPlayers.size() == 5) {
				if (isOurTeamMulti(teamName, targetTeamNames, teamPlayers, targetChampions)) {
					ourTeam = createTeamDetail(teamName, teamPlayers);
					break;
				}
			}
		}

		// 상대 팀 찾기 (우리 팀이 아닌 팀)
		for (Map.Entry<String, List<GameParticipant>> entry : teamGroups.entrySet()) {
			String teamName = entry.getKey();
			List<GameParticipant> teamPlayers = entry.getValue();

			if (teamPlayers.size() == 5) {
				if (ourTeam == null || !teamName.equals(ourTeam.teamName())) {
					opponentTeam = createTeamDetail(teamName, teamPlayers);
					break;
				}
			}
		}

		return new CombinationDetailDto.GameDetailDto(
			game.getId(),
			game.getActualGameStartTime(),
			game.getLeague().getSeasonSplit(),
			game.getLeague().getLeagueName(),
			game.getPatch(),
			game.getGameLengthSeconds(),
			ourTeam,
			opponentTeam,
			null
		);
	}

	// Multi 버전 우리 팀 판별 로직
	private boolean isOurTeamMulti(String teamName, List<String> targetTeamNames,
		List<GameParticipant> teamPlayers, List<String> targetChampions) {

		// 1. targetTeamNames가 있으면 우선 사용 (다중 팀 지원)
		if (targetTeamNames != null && !targetTeamNames.isEmpty()) {
			return targetTeamNames.contains(teamName);
		}

		// 2. targetTeamNames가 없으면 챔피언 조합으로 판별 (부분 일치)
		if (targetChampions != null && !targetChampions.isEmpty()) {
			List<String> teamChampions = teamPlayers.stream()
				.map(p -> p.getChampion().getChampionNameEn())
				.collect(Collectors.toList());

			// 타겟 챔피언이 모두 팀에 포함되어 있는지 확인
			return teamChampions.containsAll(targetChampions);
		}

		// 3. 기본값: false
		return false;
	}

	private int getPositionOrder(CombinationDetailDto.PlayerDetailDto player) {
		return switch (player.position().toLowerCase()) {
			case "top" -> 1;
			case "jng", "jungle" -> 2;
			case "mid" -> 3;
			case "bot", "adc" -> 4;
			case "sup", "support" -> 5;
			default -> 99;
		};
	}
}
