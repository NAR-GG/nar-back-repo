package com.toy.nar.common.util;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

public class NameNormalizer {

	/**
	 * 챔피언 이름을 표준 형식(첫 글자 대문자, 나머지는 소문자, 특수기호 없음)으로 변환합니다.
	 * 예: "Vel'Koz" -> "Velkoz"
	 * @param championName 원본 챔피언 이름
	 * @return 정규화된 챔피언 이름
	 */
	public static String normalizeChampionName(String championName) {
		if (!StringUtils.hasText(championName)) {
			return "";
		}

		switch (championName.toLowerCase().replaceAll("[\\s'.-]+", "")) {
			case "nunu":
				return "Nunu&Willump";
			case "renata":
				return "RenataGlasc";
			case "monkeyking":
				return "Wukong";
		}

		String cleaned = championName.replaceAll("[\\s'.-]+", "").toLowerCase();
		if (cleaned.isEmpty()) {
			return "";
		}
		return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
	}

	/**
	 * [수정] 팀 이름을 표준 형식(Title Case, 양끝 공백 없음)으로 변환합니다.
	 * 예: " t1 esports " -> "T1 Esports"
	 */
	public static String normalizeTeamName(String teamName) {
		if (!StringUtils.hasText(teamName)) {
			return "";
		}
		return toTitleCase(teamName.trim());
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
				if (word.isEmpty()) return "";
				// 모두 소문자로 바꾼 뒤 첫 글자만 대문자로
				String lowerWord = word.toLowerCase();
				return Character.toUpperCase(lowerWord.charAt(0)) + lowerWord.substring(1);
			})
			.collect(Collectors.joining(" "));
	}

}