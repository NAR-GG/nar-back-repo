package com.toy.nar.combination.strategy;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.toy.nar.game.entity.GameParticipant;

@Component
public class LeagueFilterStrategy implements FilterStrategy {
	@Override
	public List<GameParticipant> apply(List<GameParticipant> participants, Object filterValue) {
		if (filterValue instanceof List<?> leagueNames) {
			return participants.stream()
				.filter(p -> leagueNames.contains(p.getGame().getLeague().getLeagueName()))
				.collect(Collectors.toList());
		}
		return participants;
	}

	@Override
	public String getStrategyName() {
		return "league";
	}
}