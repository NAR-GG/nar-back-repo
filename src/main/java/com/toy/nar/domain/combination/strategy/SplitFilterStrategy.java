package com.toy.nar.domain.combination.strategy;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.toy.nar.domain.game.entity.GameParticipant;

@Component
public class SplitFilterStrategy implements FilterStrategy {
	@Override
	public List<GameParticipant> apply(List<GameParticipant> participants, Object filterValue) {
		if (filterValue instanceof List<?> splits) {
			return participants.stream()
				.filter(p -> splits.contains(p.getGame().getLeague().getSeasonSplit()))
				.collect(Collectors.toList());
		}
		return participants;
	}

	@Override
	public String getStrategyName() {
		return "split";
	}
}