package com.toy.nar.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.toy.nar.common.dto.GameDataCsvDto;

import lombok.extern.slf4j.Slf4j;

// GameIntegrityValidator.java 수정
@Component
@Slf4j
public class GameIntegrityValidator {

	private static final int REQUIRED_PARTICIPANTS = 10;
	private static final Set<String> REQUIRED_POSITIONS = Set.of("top", "jng", "mid", "bot", "sup");
	private static final Set<String> REQUIRED_SIDES = Set.of("Blue", "Red");

	public GameValidationResult validateGame(String gameId, List<GameDataCsvDto> participants) {
		log.debug("🔍 Validating game: {} with {} participants", gameId, participants.size());

		// 🔥 모든 참가자 데이터 로깅
		log.debug("📋 All participants for game {}:", gameId);
		for (int i = 0; i < participants.size(); i++) {
			GameDataCsvDto dto = participants.get(i);
			log.debug("  [{}] Player: '{}', Position: '{}', Side: '{}', Team: '{}'",
				i, dto.getPlayername(), dto.getPosition(), dto.getSide(), dto.getTeamname());
		}

		// 플레이어 데이터만 필터링
		List<GameDataCsvDto> playerData = participants.stream()
			.filter(this::isValidPlayerData)
			.collect(Collectors.toList());

		// 🔥 필터링 결과 로깅
		log.debug("📋 Valid players for game {} (filtered {} → {}):",
			gameId, participants.size(), playerData.size());
		for (int i = 0; i < playerData.size(); i++) {
			GameDataCsvDto dto = playerData.get(i);
			log.debug("  [{}] ✅ Player: '{}', Position: '{}', Side: '{}', Team: '{}'",
				i, dto.getPlayername(), dto.getPosition(), dto.getSide(), dto.getTeamname());
		}

		// 🔥 필터링된 데이터 상세 분석
		if (playerData.size() != REQUIRED_PARTICIPANTS) {
			log.warn("❌ Game {} validation failed:", gameId);
			log.warn("   📊 Total records: {}", participants.size());
			log.warn("   📊 Valid players: {}", playerData.size());
			log.warn("   📊 Filtered out: {}", participants.size() - playerData.size());

			// 필터링된 데이터 분석
			List<GameDataCsvDto> filteredOut = participants.stream()
				.filter(dto -> !isValidPlayerData(dto))
				.collect(Collectors.toList());

			log.warn("   🔍 Filtered out records:");
			for (GameDataCsvDto dto : filteredOut) {
				String reason = getFilterReason(dto);
				log.warn("     ❌ Player: '{}', Position: '{}', Side: '{}', Team: '{}' - Reason: {}",
					dto.getPlayername(), dto.getPosition(), dto.getSide(), dto.getTeamname(), reason);
			}

			return GameValidationResult.invalid(gameId,
				String.format("Expected %d valid players, but found %d", REQUIRED_PARTICIPANTS, playerData.size()));
		}

		// 나머지 검증 로직...
		Map<String, Map<String, Long>> positionsBySide = playerData.stream()
			.collect(Collectors.groupingBy(
				GameDataCsvDto::getSide,
				Collectors.groupingBy(
					GameDataCsvDto::getPosition,
					Collectors.counting()
				)
			));

		for (String side : REQUIRED_SIDES) {
			Map<String, Long> positions = positionsBySide.getOrDefault(side, Map.of());

			if (positions.size() != 5) {
				return GameValidationResult.invalid(gameId,
					String.format("%s side has %d positions instead of 5", side, positions.size()));
			}

			for (String position : REQUIRED_POSITIONS) {
				if (positions.getOrDefault(position, 0L) != 1L) {
					return GameValidationResult.invalid(gameId,
						String.format("%s side missing position: %s", side, position));
				}
			}
		}

		log.debug("✅ Game {} validation passed", gameId);
		return GameValidationResult.valid(gameId, playerData);
	}

	private boolean isValidPlayerData(GameDataCsvDto dto) {
		return StringUtils.hasText(dto.getPosition()) &&
			REQUIRED_POSITIONS.contains(dto.getPosition().toLowerCase()) &&
			StringUtils.hasText(dto.getPlayername()) &&
			StringUtils.hasText(dto.getSide()) &&
			REQUIRED_SIDES.contains(dto.getSide());
	}

	// 🔥 필터링 이유 분석용 메서드
	private String getFilterReason(GameDataCsvDto dto) {
		List<String> reasons = new ArrayList<>();

		if (!StringUtils.hasText(dto.getPosition())) {
			reasons.add("Empty position");
		} else if (!REQUIRED_POSITIONS.contains(dto.getPosition().toLowerCase())) {
			reasons.add("Invalid position: '" + dto.getPosition() + "'");
		}

		if (!StringUtils.hasText(dto.getPlayername())) {
			reasons.add("Empty player name");
		}

		if (!StringUtils.hasText(dto.getSide())) {
			reasons.add("Empty side");
		} else if (!REQUIRED_SIDES.contains(dto.getSide())) {
			reasons.add("Invalid side: '" + dto.getSide() + "'");
		}

		return reasons.isEmpty() ? "Unknown" : String.join(", ", reasons);
	}

	public static class GameValidationResult {
		private final String gameId;
		private final boolean isValid;
		private final String errorMessage;
		private final List<GameDataCsvDto> validPlayerData;

		private GameValidationResult(String gameId, boolean isValid, String errorMessage, List<GameDataCsvDto> validPlayerData) {
			this.gameId = gameId;
			this.isValid = isValid;
			this.errorMessage = errorMessage;
			this.validPlayerData = validPlayerData != null ? validPlayerData : Collections.emptyList();
		}

		public static GameValidationResult valid(String gameId, List<GameDataCsvDto> playerData) {
			return new GameValidationResult(gameId, true, null, playerData);
		}

		public static GameValidationResult invalid(String gameId, String errorMessage) {
			return new GameValidationResult(gameId, false, errorMessage, null);
		}

		// getters
		public String getGameId() { return gameId; }
		public boolean isValid() { return isValid; }
		public String getErrorMessage() { return errorMessage; }
		public List<GameDataCsvDto> getValidPlayerData() { return validPlayerData; }
	}
}
