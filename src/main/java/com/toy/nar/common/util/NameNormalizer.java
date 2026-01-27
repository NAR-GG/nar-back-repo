package com.toy.nar.common.util;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

public class NameNormalizer {

	/**
	 * 챔피언 이름을 표준 형식(첫 글자 대문자, 나머지는 소문자, 특수기호 없음)으로 변환합니다.
	 * 예: "Vel'Koz" -> "Velkoz"
	 * 
	 * @param championName 원본 챔피언 이름
	 * @return 정규화된 챔피언 이름
	 */
	public static String normalizeChampionName(String championName) {
		if (!StringUtils.hasText(championName)) {
			return "";
		}

		switch (championName.toLowerCase().replaceAll("[\\s'.-]+", "")) {
			case "nunu":
				return "Nunu&willump";
			case "renata":
				return "Renataglasc";
			case "monkeyking":
				return "Wukong";
		}

		String cleaned = championName.replaceAll("[\\s'.-]+", "").toLowerCase();
		if (cleaned.isEmpty()) {
			return "";
		}
		return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
	}

	private static final java.util.Map<String, String> TEAM_NAME_MAPPINGS = new java.util.HashMap<>();

	static {
		// LCK
		TEAM_NAME_MAPPINGS.put("brion", "Hanjin Brion");
		TEAM_NAME_MAPPINGS.put("gen.g esports", "Gen.g");
		TEAM_NAME_MAPPINGS.put("gen", "Gen.g");
		TEAM_NAME_MAPPINGS.put("nongshim red force", "Nongshim Redforce");

		// LPL
		TEAM_NAME_MAPPINGS.put("topesports", "Top Esports");
		TEAM_NAME_MAPPINGS.put("bilibili gaming dreamsmart", "Bilibili Gaming");
		TEAM_NAME_MAPPINGS.put("weibogaming faw audi", "Weibo Gaming");

		// LPL - Extended mappings
		TEAM_NAME_MAPPINGS.put("suzhou lng ninebot esports", "Lng Esports");
		TEAM_NAME_MAPPINGS.put("shanghai edward gaming hycan", "Edward Gaming");
		TEAM_NAME_MAPPINGS.put("thundertalkgaming", "Thundertalk Gaming");
		TEAM_NAME_MAPPINGS.put("beijing jdg intel esports", "Jd Gaming");

		TEAM_NAME_MAPPINGS.put("hangzhou lgd gaming", "Lgd Gaming");
		TEAM_NAME_MAPPINGS.put("shenzhen ninjas in pyjamas", "Ninjas In Pyjamas");
		TEAM_NAME_MAPPINGS.put("xi'an team we", "Team We");
		TEAM_NAME_MAPPINGS.put("omg", "Omg");
		TEAM_NAME_MAPPINGS.put("anyone's legend", "Anyone's Legend");
		TEAM_NAME_MAPPINGS.put("invictus gaming", "Invictus Gaming");
		TEAM_NAME_MAPPINGS.put("funplus phoenix", "Funplus Phoenix");
		TEAM_NAME_MAPPINGS.put("royal never give up", "Rng");
		TEAM_NAME_MAPPINGS.put("edward gaming hycan", "Edward Gaming");
		TEAM_NAME_MAPPINGS.put("lng esports ninebot", "Lng Esports");
		TEAM_NAME_MAPPINGS.put("thunder talk gaming", "Thunder Talk Gaming");
		TEAM_NAME_MAPPINGS.put("ultra prime", "Ultra Prime");
		TEAM_NAME_MAPPINGS.put("rare atom", "Rare Atom");
		TEAM_NAME_MAPPINGS.put("jd gaming", "Jd Gaming");
	}

	/**
	 * [수정] 팀 이름을 표준 형식(Title Case, 양끝 공백 없음)으로 변환합니다.
	 * 예: " t1 esports " -> "T1 Esports"
	 */
	public static String normalizeTeamName(String teamName) {
		if (!StringUtils.hasText(teamName)) {
			return "";
		}

		String trimmed = teamName.trim();
		String lower = trimmed.toLowerCase();

		if (TEAM_NAME_MAPPINGS.containsKey(lower)) {
			return TEAM_NAME_MAPPINGS.get(lower);
		}

		return toTitleCase(trimmed);
	}

	/**
	 * [수정] 플레이어 이름을 표준 형식(Title Case, 양끝 공백 없음)으로 변환합니다.
	 * 예: " hide on bush " -> "Hide On Bush"
	 */
	public static String normalizePlayerName(String playerName) {
		if (!StringUtils.hasText(playerName)) {
			return "";
		}
		return toTitleCase(playerName.trim());
	}

	/**
	 * [신규] 문자열을 Title Case로 변환하는 헬퍼 메서드
	 * 각 단어의 첫 글자를 대문자로 만듭니다.
	 */
	private static String toTitleCase(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		// 공백을 기준으로 단어를 분리하여 각 단어의 첫 글자를 대문자로 만들고 다시 합침
		return Arrays.stream(input.split("\\s+"))
				.map(word -> {
					if (word.isEmpty())
						return "";
					// 모두 소문자로 바꾼 뒤 첫 글자만 대문자로
					String lowerWord = word.toLowerCase();
					return Character.toUpperCase(lowerWord.charAt(0)) + lowerWord.substring(1);
				})
				.collect(Collectors.joining(" "));
	}

}