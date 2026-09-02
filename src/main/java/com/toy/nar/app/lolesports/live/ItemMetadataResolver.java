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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemMetadataResolver {

	private static final String TAG_TRINKET = "Trinket";
	private static final String TAG_CONSUMABLE = "Consumable";
	/** 퀘스트 없이 채울 수 있는 코어 칸 수. 7번째는 퀘스트 칸으로 뺀다. */
	private static final int CORE_SLOTS = 6;

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

	/**
	 * 아이템을 섹션별로 갈라 준다 — 코어 / 퀘스트 칸 / 장신구 / 소모품.
	 *
	 * <p>분류 기준은 ddragon 태그다: {@code Trinket} 이면 장신구(3340·3363·3364 뿐),
	 * {@code Consumable} 이면 소모품(제어와드·물약·영약), 나머지는 코어(장화 포함)다.
	 * ddragon 에 없는 id 는 코어로 둔다(구버전 아이템이 남은 경우).
	 *
	 * <p>코어 7번째는 2026 퀘스트로 열리는 칸이라 {@code questItemImageUrl} 로 따로 뺀다
	 * (원딜은 신발 포함, 서포터는 제어와드 포함 조건). 실데이터에서 코어는 7개를 넘지 않지만,
	 * 넘으면 하위템 잔재이므로 뒤쪽을 버린다.
	 */
	public ItemGroups resolveItemGroups(List<Integer> itemIds) {
		ensureLoaded();
		List<ResolvedItem> resolved = new ArrayList<>();
		for (Integer itemId : itemIds) {
			if (itemId == null || itemId <= 0) {
				continue;
			}
			ItemInfo itemInfo = itemsById.get(itemId);
			if (itemInfo == null) {
				// ddragon 이 모르는 id(폐기된 아이템 등)는 칸을 만들지 않는다 — 규칙상 폴백 URL 도 404 다.
				// 실측 3097 이 그런 경우(전체 아이템 등장 1% 미만). 메타데이터 로드 실패면 전부 여기로
				// 빠져 섹션이 비는데, 기존 itemImageUrls 가 그대로 남아 있어 화면이 빈칸만 되지는 않는다.
				continue;
			}
			String imageUrl = itemInfo.imageUrl() != null && !itemInfo.imageUrl().isBlank()
					? itemInfo.imageUrl()
					: buildFallbackImageUrl(itemId);
			resolved.add(new ResolvedItem(imageUrl, itemInfo.tags()));
		}
		return groupItems(resolved);
	}

	/** 분류 규칙 본문. ddragon 로드와 분리해 단위 테스트한다. */
	static ItemGroups groupItems(List<ResolvedItem> items) {
		List<String> core = new ArrayList<>();
		String questItemImageUrl = null;
		String trinketImageUrl = null;
		List<String> consumables = new ArrayList<>();

		for (ResolvedItem item : items) {
			if (item.tags().contains(TAG_TRINKET)) {
				// 장신구는 한 칸뿐이다. 잔재로 둘이 남으면 먼저 산 쪽을 남긴다.
				if (trinketImageUrl == null) {
					trinketImageUrl = item.imageUrl();
				}
			} else if (item.tags().contains(TAG_CONSUMABLE)) {
				consumables.add(item.imageUrl());
			} else if (core.size() < CORE_SLOTS) {
				core.add(item.imageUrl());
			} else if (questItemImageUrl == null) {
				questItemImageUrl = item.imageUrl();
			}
		}

		return new ItemGroups(core, questItemImageUrl, trinketImageUrl, consumables);
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
					Set<String> tags = new LinkedHashSet<>();
					for (JsonNode tag : itemNode.path("tags")) {
						tags.add(tag.asText(""));
					}
					newItemsById.put(itemId, new ItemInfo(name, imageUrl, tags));
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

	private record ItemInfo(String name, String imageUrl, Set<String> tags) {
	}

	/** 분류 입력 — 이미지 URL 과 ddragon 태그 쌍. */
	record ResolvedItem(String imageUrl, Set<String> tags) {
	}

	/** 스코어보드 아이템 칸 구성. 장신구·퀘스트 칸은 없으면 null. */
	public record ItemGroups(
			List<String> coreImageUrls,
			String questItemImageUrl,
			String trinketImageUrl,
			List<String> consumableImageUrls) {
	}
}
