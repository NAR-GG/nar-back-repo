package com.toy.nar.app.lolesports.live;

import com.fasterxml.jackson.databind.JsonNode;
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

	private final WebClient webClient;

	@Value("${lolesports.live.rune-locale:ko_KR}")
	private String runeLocale;

	@Value("${lolesports.live.rune-refresh-minutes:360}")
	private long refreshMinutes;

	private volatile Instant loadedAt = Instant.EPOCH;
	private volatile Map<Integer, String> runeNamesById = new HashMap<>(STAT_SHARD_NAMES);
	private volatile Map<Integer, String> styleNamesById = new HashMap<>();

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

			for (JsonNode style : runeTrees) {
				int styleId = style.path("id").asInt(-1);
				String styleName = style.path("name").asText("");
				if (styleId > 0 && !styleName.isBlank()) {
					newStyleNames.put(styleId, styleName);
				}

				JsonNode slots = style.path("slots");
				if (!slots.isArray()) {
					continue;
				}
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
					}
				}
			}

			this.runeNamesById = newRuneNames;
			this.styleNamesById = newStyleNames;
			this.loadedAt = Instant.now();
			log.info("Loaded rune metadata from Data Dragon: version={}, runeCount={}, styleCount={}",
					latestVersion, newRuneNames.size(), newStyleNames.size());
		} catch (Exception e) {
			log.warn("Failed to refresh rune metadata: {}", e.getMessage());
		}
	}
}

