package com.toy.nar.combination.strategy;

import java.util.List;

import com.toy.nar.game.entity.GameParticipant;

public interface FilterStrategy {
	List<GameParticipant> apply(List<GameParticipant> participants, Object filterValue);

	String getStrategyName();
}
