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
		List<String> runeNames,
		/** 설치한 와드 수(누적). 피드 wardsPlaced. 옛 스냅샷은 null. */
		Integer wardsPlaced,
		/** 부순 상대 와드 수(누적). 피드 wardsDestroyed. */
		Integer wardsDestroyed) {

	/** 와드 필드 없는 옛 시그니처. 기존 호출처·테스트 호환용. */
	public LiveParticipantState(
			Integer participantId, String teamSide, String role, String playerName, String esportsPlayerId,
			String championName, Integer level, Integer kills, Integer deaths, Integer assists,
			Integer totalGoldEarned, Integer creepScore, Double killParticipation, Double championDamageShare,
			List<Integer> itemIds, List<String> itemNames, List<String> itemImageUrls, String perksJson,
			String primaryStyleName, String subStyleName, List<String> runeNames) {
		this(participantId, teamSide, role, playerName, esportsPlayerId, championName, level, kills, deaths,
				assists, totalGoldEarned, creepScore, killParticipation, championDamageShare, itemIds, itemNames,
				itemImageUrls, perksJson, primaryStyleName, subStyleName, runeNames, null, null);
	}
}
