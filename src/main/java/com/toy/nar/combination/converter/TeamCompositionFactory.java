package com.toy.nar.combination.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.toy.nar.combination.domain.TeamComposition;
import com.toy.nar.game.entity.Game;
import com.toy.nar.game.entity.GameParticipant;

@Component
public class TeamCompositionFactory {

	public TeamComposition createFromParticipants(List<GameParticipant> participants) {
		if (participants == null || participants.isEmpty()) {
			throw new IllegalArgumentException("Participants cannot be null or empty");
		}

		validateParticipants(participants);

		GameParticipant first = participants.get(0);
		Game game = first.getGame();

		List<String> champions = participants.stream()
			.map(p -> p.getChampion().getChampionNameEn())
			.sorted()
			.collect(Collectors.toList());

		return new TeamComposition(
			game.getId(),
			first.getTeam().getName(),
			champions,
			first.getIsWin(),
			game.getPatch(),
			game.getLeague().getLeagueName(),
			game.getGameDate()
		);
	}

	private void validateParticipants(List<GameParticipant> participants) {
		Long gameId = participants.get(0).getGame().getId();
		String teamName = participants.get(0).getTeam().getName();

		boolean allSameGame = participants.stream()
			.allMatch(p -> p.getGame().getId().equals(gameId));

		boolean allSameTeam = participants.stream()
			.allMatch(p -> p.getTeam().getName().equals(teamName));

		if (!allSameGame || !allSameTeam) {
			throw new IllegalArgumentException("All participants must be from the same game and team");
		}
	}
}