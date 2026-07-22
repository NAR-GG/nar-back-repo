package com.toy.nar.app.riot;

import java.util.Map;

/**
 * Riot 지역(region) 태그와 플랫폼 라우팅 값 매핑.
 * - 지역 태그: 크롤링 소스(trackingthepros)의 [KR]/[EUW]/[NA] 등.
 * - 플랫폼 라우팅 값: spectator-v5 등 플랫폼 API 서브도메인(kr/euw1/na1 ...).
 * account-v1(PUUID 해석)은 글로벌 라우팅이라 지역 무관하지만,
 * spectator(현재 게임)는 반드시 해당 플랫폼 호스트로 호출해야 게임이 보인다.
 */
public final class RiotPlatform {

	static final String DEFAULT_PLATFORM = "KR";

	private RiotPlatform() {
	}

	// 지역 태그 → 플랫폼 라우팅 값. 이미 라우팅 값(EUW1 등)이면 그대로 통과.
	private static final Map<String, String> REGION_TO_PLATFORM = Map.ofEntries(
			Map.entry("KR", "KR"),
			Map.entry("EUW", "EUW1"),
			Map.entry("EUNE", "EUN1"),
			Map.entry("NA", "NA1"),
			Map.entry("BR", "BR1"),
			Map.entry("JP", "JP1"),
			Map.entry("OCE", "OC1"),
			Map.entry("OC", "OC1"),
			Map.entry("LAN", "LA1"),
			Map.entry("LAS", "LA2"),
			Map.entry("TR", "TR1"),
			Map.entry("RU", "RU"),
			Map.entry("VN", "VN2"),
			Map.entry("TW", "TW2"),
			Map.entry("SG", "SG2"),
			Map.entry("TH", "TH2"),
			Map.entry("PH", "PH2"));

	// 플랫폼 라우팅 값 → op.gg 지역 코드.
	private static final Map<String, String> PLATFORM_TO_OPGG = Map.ofEntries(
			Map.entry("KR", "kr"),
			Map.entry("EUW1", "euw"),
			Map.entry("EUN1", "eune"),
			Map.entry("NA1", "na"),
			Map.entry("BR1", "br"),
			Map.entry("JP1", "jp"),
			Map.entry("OC1", "oce"),
			Map.entry("LA1", "lan"),
			Map.entry("LA2", "las"),
			Map.entry("TR1", "tr"),
			Map.entry("RU", "ru"),
			Map.entry("VN2", "vn"),
			Map.entry("TW2", "tw"),
			Map.entry("SG2", "sg"),
			Map.entry("TH2", "th"),
			Map.entry("PH2", "ph"));

	/** 지역 태그(KR/EUW/NA...) 또는 플랫폼 값을 플랫폼 라우팅 값(KR/EUW1/NA1...)으로 정규화. null/빈값은 KR. */
	public static String toPlatform(String region) {
		if (region == null || region.isBlank()) {
			return DEFAULT_PLATFORM;
		}
		String key = region.trim().toUpperCase();
		return REGION_TO_PLATFORM.getOrDefault(key, key);
	}

	/** 플랫폼 API 호스트. 예: KR → https://kr.api.riotgames.com, EUW → https://euw1.api.riotgames.com */
	public static String apiHost(String platform) {
		return "https://" + toPlatform(platform).toLowerCase() + ".api.riotgames.com";
	}

	/** op.gg 소환사 페이지 지역 코드. 예: KR → kr, EUW1 → euw. 미지원 플랫폼은 kr 폴백. */
	public static String opggRegion(String platform) {
		return PLATFORM_TO_OPGG.getOrDefault(toPlatform(platform), "kr");
	}
}
