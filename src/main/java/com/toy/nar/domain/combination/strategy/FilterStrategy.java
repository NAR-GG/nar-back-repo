package com.toy.nar.domain.combination.strategy;

import java.util.List;

import com.toy.nar.domain.game.entity.GameParticipant;

public interface FilterStrategy {
	List<GameParticipant> apply(List<GameParticipant> participants, Object filterValue);

	String getStrategyName();
}
