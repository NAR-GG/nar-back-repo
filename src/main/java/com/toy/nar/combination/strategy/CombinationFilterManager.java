package com.toy.nar.combination.strategy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.toy.nar.game.entity.GameParticipant;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CombinationFilterManager {

	private final Map<String, FilterStrategy> strategies;

	public CombinationFilterManager(List<FilterStrategy> filterStrategies) {
		this.strategies = filterStrategies.stream()
			.collect(Collectors.toMap(
				FilterStrategy::getStrategyName,
				strategy -> strategy
			));
		log.info("🔧 Registered filter strategies: {}", strategies.keySet());
	}

	public List<GameParticipant> applyFilters(List<GameParticipant> participants,
		MultiCombinationFilterDto filter) {
		List<GameParticipant> result = participants;

		log.debug("🔄 Starting filter application with {} participants", participants.size());

		// 리그 필터 적용
		if (filter.getLeagueNames() != null && !filter.getLeagueNames().isEmpty()) {
			result = applyStrategy("league", result, filter.getLeagueNames());
		}

		// 스플릿 필터 적용
		if (filter.getSplits() != null && !filter.getSplits().isEmpty()) {
			result = applyStrategy("split", result, filter.getSplits());
		}

		// 팀 필터 적용
		if (filter.getTeamNames() != null && !filter.getTeamNames().isEmpty()) {
			result = applyStrategy("team", result, filter.getTeamNames());
		}

		log.debug("✅ Filter application complete: {} participants", result.size());
		return result;
	}

	private List<GameParticipant> applyStrategy(String strategyName,
		List<GameParticipant> participants,
		List<String> filterValues) {
		FilterStrategy strategy = strategies.get(strategyName);
		if (strategy == null) {
			log.warn("⚠️ Strategy not found: {}", strategyName);
			return participants;
		}

		List<GameParticipant> result = strategy.apply(participants, filterValues);
		log.debug("📊 After {} filter: {} participants (filtered by: {})",
			strategyName, result.size(), filterValues);
		return result;
	}

	public boolean shouldUseMemoryFiltering(MultiCombinationFilterDto filter) {
		return filter.hasMultipleFilters();
	}
}