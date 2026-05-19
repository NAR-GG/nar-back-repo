package com.toy.nar.app.lolesports.live.dto;

import java.util.List;

public record LiveParticipantState(
		Integer participantId,
		String teamSide,
		String role,
		String playerName,
		String esportsPlayerId,
		String championName,
		Integer level,
		Integer kills,
		Integer deaths,
		Integer assists,
		Integer totalGoldEarned,
		Integer creepScore,
		Double killParticipation,
		Double championDamageShare,
		List<Integer> itemIds,
		List<String> itemNames,
		List<String> itemImageUrls,
		String perksJson,
		String primaryStyleName,
		String subStyleName,
		List<String> runeNames) {
}
