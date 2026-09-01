package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuneMetadataResolver {

	private static final Map<Integer, String> STAT_SHARD_NAMES = Map.of(
			5001, "체력 +65",
			5002, "방어력 +10",
			5003, "마법 저항력 +10",
			5005, "공격 속도 +10%",
			5007, "스킬 가속 +8",
			5008, "적응형 능력치 +9",
			5010, "이동 속도 +2%",
			5011, "강인함 +10% 및 둔화 저항 +10%");

	private static final String ICON_BASE = "https://ddragon.leagueoflegends.com/cdn/img/";

	private final WebClient webClient;
	private final ObjectMapper objectMapper;

	@Value("${lolesports.live.rune-locale:ko_KR}")
	private String runeLocale;

	@Value("${lolesports.live.rune-refresh-minutes:360}")
	private long refreshMinutes;

	private volatile Instant loadedAt = Instant.EPOCH;
	private volatile Map<Integer, String> runeNamesById = new HashMap<>(STAT_SHARD_NAMES);
	private volatile Map<Integer, String> styleNamesById = new HashMap<>();
	private volatile Map<Integer, String> styleIconsById = new HashMap<>();
	private volatile Map<Integer, String> runeIconsById = new HashMap<>();
	/** 룬 id → 트리 내 슬롯 인덱스. 0 이 핵심룬(키스톤) 슬롯이다. */
	private volatile Map<Integer, Integer> runeSlotById = new HashMap<>();

	public String resolveStyleName(Integer styleId) {
		if (styleId == null) {
			return null;
		}
		ensureLoaded();
		return styleNamesById.getOrDefault(styleId, "스타일-" + styleId);
	}

	public List<String> resolveRuneNames(List<Integer> runeIds) {
		ensureLoaded();
		List<String> names = new ArrayList<>();
		for (Integer runeId : runeIds) {
			if (runeId == null) {
				continue;
			}
			names.add(runeNamesById.getOrDefault(runeId, "룬-" + runeId));
		}
		return names;
	}

	/**
	 * 스코어보드 한 줄에 들어갈 룬 아이콘 2개(핵심룬 + 부트리)를 뽑는다.
	 *
	 * <p>핵심룬은 {@code perks} 배열에서 슬롯 인덱스 0 인 룬을 찾아 고른다(배열 순서에 의존하지 않는다).
	 * 파편(5xxx)은 ddragon runesReforged 에 없어 아이콘이 없다 — 스코어보드 행에도 안 들어가므로 뽑지 않는다.
	 */
	public RuneIcons resolveRuneIcons(String perksJson) {
		if (perksJson == null || perksJson.isBlank() || "{}".equals(perksJson)) {
			return new RuneIcons(null, null);
		}
		ensureLoaded();
		try {
			JsonNode node = objectMapper.readTree(perksJson);
			String keystoneIconUrl = null;
			for (JsonNode perk : node.path("perks")) {
				if (!perk.isNumber()) {
					continue;
				}
				int runeId = perk.asInt();
				if (Integer.valueOf(0).equals(runeSlotById.get(runeId))) {
					keystoneIconUrl = runeIconsById.get(runeId);
					break;
				}
			}
			JsonNode subStyleId = node.path("subStyleId");
			String subStyleIconUrl = subStyleId.isNumber() ? styleIconsById.get(subStyleId.asInt()) : null;
			return new RuneIcons(keystoneIconUrl, subStyleIconUrl);
		} catch (Exception e) {
			return new RuneIcons(null, null);
		}
	}

	private void ensureLoaded() {
		Instant now = Instant.now();
		if (!runeNamesById.isEmpty() && Duration.between(loadedAt, now).toMinutes() < refreshMinutes) {
			return;
		}
		synchronized (this) {
			if (!runeNamesById.isEmpty() && Duration.between(loadedAt, Instant.now()).toMinutes() < refreshMinutes) {
				return;
			}
			loadFromDataDragon();
		}
	}

	private void loadFromDataDragon() {
		try {
			JsonNode versions = webClient.get()
					.uri("https://ddragon.leagueoflegends.com/api/versions.json")
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();

			if (versions == null || !versions.isArray() || versions.isEmpty()) {
				log.warn("Failed to load Data Dragon versions.");
				return;
			}

			String latestVersion = versions.get(0).asText();
			JsonNode runeTrees = webClient.get()
					.uri("https://ddragon.leagueoflegends.com/cdn/{version}/data/{locale}/runesReforged.json",
							latestVersion, runeLocale)
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();

			if (runeTrees == null || !runeTrees.isArray()) {
				log.warn("Failed to load Data Dragon runes for version: {}", latestVersion);
				return;
			}

			Map<Integer, String> newRuneNames = new HashMap<>(STAT_SHARD_NAMES);
			Map<Integer, String> newStyleNames = new HashMap<>();
			Map<Integer, String> newStyleIcons = new HashMap<>();
			Map<Integer, String> newRuneIcons = new HashMap<>();
			Map<Integer, Integer> newRuneSlots = new HashMap<>();

			for (JsonNode style : runeTrees) {
				int styleId = style.path("id").asInt(-1);
				String styleName = style.path("name").asText("");
				if (styleId > 0 && !styleName.isBlank()) {
					newStyleNames.put(styleId, styleName);
				}
				String styleIcon = style.path("icon").asText("");
				if (styleId > 0 && !styleIcon.isBlank()) {
					newStyleIcons.put(styleId, ICON_BASE + styleIcon);
				}

				JsonNode slots = style.path("slots");
				if (!slots.isArray()) {
					continue;
				}
				int slotIndex = 0;
				for (JsonNode slot : slots) {
					JsonNode runes = slot.path("runes");
					if (!runes.isArray()) {
						continue;
					}
					for (JsonNode rune : runes) {
						int runeId = rune.path("id").asInt(-1);
						String runeName = rune.path("name").asText("");
						if (runeId > 0 && !runeName.isBlank()) {
							newRuneNames.put(runeId, runeName);
						}
						String runeIcon = rune.path("icon").asText("");
						if (runeId > 0 && !runeIcon.isBlank()) {
							newRuneIcons.put(runeId, ICON_BASE + runeIcon);
							newRuneSlots.put(runeId, slotIndex);
						}
					}
					slotIndex++;
				}
			}

			this.runeNamesById = newRuneNames;
			this.styleNamesById = newStyleNames;
			this.styleIconsById = newStyleIcons;
			this.runeIconsById = newRuneIcons;
			this.runeSlotById = newRuneSlots;
			this.loadedAt = Instant.now();
			log.info("Loaded rune metadata from Data Dragon: version={}, runeCount={}, styleCount={}",
					latestVersion, newRuneNames.size(), newStyleNames.size());
		} catch (Exception e) {
			log.warn("Failed to refresh rune metadata: {}", e.getMessage());
		}
	}

	/** 스코어보드 행에 쓰는 룬 아이콘 URL 쌍. 로드 실패·미매핑이면 각 필드가 null. */
	public record RuneIcons(String keystoneIconUrl, String subStyleIconUrl) {
	}
}
