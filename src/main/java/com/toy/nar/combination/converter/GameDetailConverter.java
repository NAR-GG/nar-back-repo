package com.toy.nar.combination.converter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.toy.nar.combination.dto.CombinationDetailDto;
import com.toy.nar.game.entity.Game;
import com.toy.nar.game.entity.GameParticipant;

@Component
public class GameDetailConverter {

	public List<CombinationDetailDto.GameDetailDto> convertToGameDetails(
		List<GameParticipant> participants,
		String targetTeamName,
		List<String> targetChampions) {  // 🔥 타겟 챔피언 목록 추가

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

		// 🔥 팀별로 그룹화
		Map<String, List<GameParticipant>> teamGroups = gameParticipants.stream()
			.collect(Collectors.groupingBy(p -> p.getTeam().getName()));

		// 🔥 우리 팀과 상대 팀 분리
		CombinationDetailDto.TeamDetailDto ourTeam = null;
		CombinationDetailDto.TeamDetailDto opponentTeam = null;

		for (Map.Entry<String, List<GameParticipant>> entry : teamGroups.entrySet()) {
			String teamName = entry.getKey();
			List<GameParticipant> teamPlayers = entry.getValue();

			if (teamPlayers.size() == 5) {
				CombinationDetailDto.TeamDetailDto teamDetail = createTeamDetail(teamName, teamPlayers);

				// 🔥 우리 팀 판별 로직 개선
				if (isOurTeam(teamName, targetTeamName, teamPlayers, targetChampions)) {
					ourTeam = teamDetail;
				} else {
					opponentTeam = teamDetail;
				}
			}
		}

		return new CombinationDetailDto.GameDetailDto(
			game.getId(),
			game.getGameDate(),
			game.getLeague().getSeasonSplit(),
			game.getLeague().getLeagueName(),
			game.getPatch(),
			game.getGameLengthSeconds(),
			ourTeam,
			opponentTeam
		);
	}

	// 🔥 우리 팀 판별 로직
	private boolean isOurTeam(String teamName, String targetTeamName,
		List<GameParticipant> teamPlayers, List<String> targetChampions) {

		// 1. targetTeamName이 있으면 우선 사용
		if (targetTeamName != null && !targetTeamName.isEmpty()) {
			return teamName.equals(targetTeamName);
		}

		// 2. targetTeamName이 없으면 챔피언 조합으로 판별
		if (targetChampions != null && !targetChampions.isEmpty()) {
			List<String> teamChampions = teamPlayers.stream()
				.map(p -> p.getChampion().getChampionNameEn())
				.sorted()
				.collect(Collectors.toList());

			List<String> sortedTargetChampions = targetChampions.stream()
				.sorted()
				.collect(Collectors.toList());

			return teamChampions.equals(sortedTargetChampions);
		}

		// 3. 기본값: 첫 번째 팀을 우리 팀으로 설정
		return true;
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
