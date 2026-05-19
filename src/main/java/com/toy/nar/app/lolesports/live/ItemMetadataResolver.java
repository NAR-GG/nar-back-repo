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
public class ItemMetadataResolver {

	private final WebClient webClient;

	@Value("${lolesports.live.item-locale:ko_KR}")
	private String itemLocale;

	@Value("${lolesports.live.item-refresh-minutes:360}")
	private long refreshMinutes;

	private volatile Instant loadedAt = Instant.EPOCH;
	private volatile String dataDragonVersion = null;
	private volatile Map<Integer, ItemInfo> itemsById = new HashMap<>();

	public List<String> resolveItemNames(List<Integer> itemIds) {
		ensureLoaded();
		List<String> names = new ArrayList<>();
		for (Integer itemId : itemIds) {
			if (itemId == null || itemId <= 0) {
				continue;
			}
			ItemInfo itemInfo = itemsById.get(itemId);
			names.add(itemInfo != null ? itemInfo.name() : "아이템-" + itemId);
		}
		return names;
	}

	public List<String> resolveItemImageUrls(List<Integer> itemIds) {
		ensureLoaded();
		List<String> imageUrls = new ArrayList<>();
		for (Integer itemId : itemIds) {
			if (itemId == null || itemId <= 0) {
				continue;
			}
			ItemInfo itemInfo = itemsById.get(itemId);
			if (itemInfo != null && itemInfo.imageUrl() != null && !itemInfo.imageUrl().isBlank()) {
				imageUrls.add(itemInfo.imageUrl());
				continue;
			}
			imageUrls.add(buildFallbackImageUrl(itemId));
		}
		return imageUrls;
	}

	private void ensureLoaded() {
		Instant now = Instant.now();
		if (!itemsById.isEmpty() && Duration.between(loadedAt, now).toMinutes() < refreshMinutes) {
			return;
		}
		synchronized (this) {
			if (!itemsById.isEmpty() && Duration.between(loadedAt, Instant.now()).toMinutes() < refreshMinutes) {
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
				log.warn("Failed to load Data Dragon versions for items.");
				return;
			}

			String latestVersion = versions.get(0).asText();
			JsonNode itemDataResponse = webClient.get()
					.uri("https://ddragon.leagueoflegends.com/cdn/{version}/data/{locale}/item.json",
							latestVersion, itemLocale)
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();

			JsonNode itemData = itemDataResponse == null ? null : itemDataResponse.path("data");
			if (itemData == null || !itemData.isObject()) {
				log.warn("Failed to load Data Dragon item metadata for version: {}", latestVersion);
				return;
			}

			Map<Integer, ItemInfo> newItemsById = new HashMap<>();
			itemData.fields().forEachRemaining(entry -> {
				try {
					int itemId = Integer.parseInt(entry.getKey());
					JsonNode itemNode = entry.getValue();
					String name = itemNode.path("name").asText("");
					String imageFull = itemNode.path("image").path("full").asText("");
					String imageUrl = imageFull.isBlank()
							? null
							: "https://ddragon.leagueoflegends.com/cdn/" + latestVersion + "/img/item/" + imageFull;
					newItemsById.put(itemId, new ItemInfo(name, imageUrl));
				} catch (NumberFormatException ignore) {
					// Ignore non-numeric item id keys.
				}
			});

			this.itemsById = newItemsById;
			this.dataDragonVersion = latestVersion;
			this.loadedAt = Instant.now();
			log.info("Loaded item metadata from Data Dragon: version={}, itemCount={}",
					latestVersion, newItemsById.size());
		} catch (Exception e) {
			log.warn("Failed to refresh item metadata: {}", e.getMessage());
		}
	}

	private String buildFallbackImageUrl(Integer itemId) {
		if (dataDragonVersion == null || dataDragonVersion.isBlank()) {
			return null;
		}
		return "https://ddragon.leagueoflegends.com/cdn/" + dataDragonVersion + "/img/item/" + itemId + ".png";
	}

	private record ItemInfo(String name, String imageUrl) {
	}
}
