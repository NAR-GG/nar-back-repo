package com.toy.nar.service;

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
		String cleaned = championName.replaceAll("[\\s'.-]+", "").toLowerCase();
		if (cleaned.isEmpty()) {
			return "";
		}
		return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
	}

}