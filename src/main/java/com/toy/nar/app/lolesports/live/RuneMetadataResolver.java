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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 룬 id → 이름·아이콘·설명·트리 소속. 피드 {@code perkMetadata} 는 id 숫자만 주므로 메타는 전부 여기서 붙인다.
 *
 * <p>룬(8xxx·9xxx)은 ddragon {@code runesReforged.json} 에서 6시간마다 적재한다. 파편(5xxx)은 ddragon 에 없어
 * CommunityDragon 아이콘 + 수치 상수를 코드에 둔다(패치마다 바뀌지 않는 값).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuneMetadataResolver {

	private static final String ICON_BASE = "https://ddragon.leagueoflegends.com/cdn/img/";
	private static final String SHARD_ICON_BASE =
			"https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/perk-images/statmods/";

	/**
	 * 파편(능력치 조각) 표. 실데이터 20,320행에서 등장한 건 7종(5001·5005·5007·5008·5010·5011·5013)이고
	 * 5002·5003·5012 는 옛 패치 잔재로 0행이지만 id 가 오면 이름은 나가게 둔다.
	 */
	private static final Map<Integer, Shard> STAT_SHARDS = Map.ofEntries(
			Map.entry(5008, new Shard("적응형 능력치", SHARD_ICON_BASE + "statmodsadaptiveforceicon.png", "+9")),
			Map.entry(5005, new Shard("공격 속도", SHARD_ICON_BASE + "statmodsattackspeedicon.png", "+10%")),
			Map.entry(5007, new Shard("스킬 가속", SHARD_ICON_BASE + "statmodscdrscalingicon.png", "+8")),
			Map.entry(5010, new Shard("이동 속도", SHARD_ICON_BASE + "statmodsmovementspeedicon.png", "+2%")),
			Map.entry(5001, new Shard("체력 증가", SHARD_ICON_BASE + "statmodshealthplusicon.png", "+65")),
			Map.entry(5011, new Shard("체력", SHARD_ICON_BASE + "statmodshealthscalingicon.png", "+10~180")),
			Map.entry(5013, new Shard("강인함 및 둔화 저항", SHARD_ICON_BASE + "statmodstenacityicon.png", "+10%")),
			Map.entry(5002, new Shard("방어력", SHARD_ICON_BASE + "statmodsarmoricon.png", null)),
			Map.entry(5003, new Shard("마법 저항력", SHARD_ICON_BASE + "statmodsmagicresicon.png", null)),
			Map.entry(5012, new Shard("저항 증가", SHARD_ICON_BASE + "statmodsadaptiveforcescalingicon.png", null)));

	private final WebClient webClient;
	private final ObjectMapper objectMapper;

	@Value("${lolesports.live.rune-locale:ko_KR}")
	private String runeLocale;

	@Value("${lolesports.live.rune-refresh-minutes:360}")
	private long refreshMinutes;

	private volatile Instant loadedAt = Instant.EPOCH;
	private volatile Catalog catalog = Catalog.empty();

	public String resolveStyleName(Integer styleId) {
		if (styleId == null) {
			return null;
		}
		ensureLoaded();
		return catalog.styleName(styleId);
	}

	public List<String> resolveRuneNames(List<Integer> runeIds) {
		ensureLoaded();
		List<String> names = new ArrayList<>();
		for (Integer runeId : runeIds) {
			if (runeId != null) {
				names.add(catalog.runeName(runeId));
			}
		}
		return names;
	}

	/** 스코어보드 한 줄용 아이콘 2개(핵심룬 + 부트리). {@link #resolveRuneBuild} 의 축약이다. */
	public RuneIcons resolveRuneIcons(String perksJson) {
		RuneBuild build = resolveRuneBuild(perksJson);
		if (build == null) {
			return new RuneIcons(null, null);
		}
		String keystone = build.primary().runes().isEmpty() ? null : build.primary().runes().get(0).iconUrl();
		return new RuneIcons(keystone, build.sub().styleIconUrl());
	}

	/**
	 * 빌드 시트용 룬 전체 — 주 트리 4개(핵심룬 먼저) · 부 트리 2개 · 파편 2~3개.
	 *
	 * <p>어느 트리 소속인지는 배열 순서가 아니라 <b>룬 id 의 소속 트리</b>로 가른다. 파편은 같은 걸 두 칸에
	 * 찍으면 피드가 하나로 합쳐 보내므로 2개일 수 있다(전체의 25%). 복원은 불가능하니 그대로 내린다.
	 * perks 가 없으면 null.
	 */
	public RuneBuild resolveRuneBuild(String perksJson) {
		if (perksJson == null || perksJson.isBlank() || "{}".equals(perksJson)) {
			return null;
		}
		ensureLoaded();
		try {
			JsonNode node = objectMapper.readTree(perksJson);
			Integer primaryStyleId = node.path("styleId").isNumber() ? node.path("styleId").asInt() : null;
			Integer subStyleId = node.path("subStyleId").isNumber() ? node.path("subStyleId").asInt() : null;
			List<Integer> perks = new ArrayList<>();
			for (JsonNode perk : node.path("perks")) {
				if (perk.isNumber()) {
					perks.add(perk.asInt());
				}
			}
			return assemble(primaryStyleId, subStyleId, perks, catalog);
		} catch (Exception e) {
			return null;
		}
	}

	/** 분류 본문. 네트워크 로드와 분리해 단위 테스트한다. */
	static RuneBuild assemble(Integer primaryStyleId, Integer subStyleId, List<Integer> perks, Catalog catalog) {
		List<Integer> primaryIds = new ArrayList<>();
		List<Integer> subIds = new ArrayList<>();
		List<Shard> shards = new ArrayList<>();

		for (Integer id : perks) {
			if (id >= 5000 && id < 6000) {
				Shard shard = STAT_SHARDS.get(id);
				shards.add(shard != null ? shard : new Shard("파편-" + id, null, null));
				continue;
			}
			Integer styleId = catalog.runeStyleById.get(id);
			if (styleId != null && styleId.equals(subStyleId)) {
				subIds.add(id);
			} else if (styleId != null && styleId.equals(primaryStyleId)) {
				primaryIds.add(id);
			} else if (primaryIds.size() < 4) {
				// 메타에 없는 룬(ddragon 미반영 신규 룬 등). 배열 순서상 주 4 → 부 2 규칙으로 폴백.
				primaryIds.add(id);
			} else {
				subIds.add(id);
			}
		}
		// 핵심룬(슬롯 0)이 앞에 오도록. 부 트리는 슬롯 순.
		Comparator<Integer> bySlot = Comparator.comparingInt(id -> catalog.runeSlotById.getOrDefault(id, 99));
		primaryIds.sort(bySlot);
		subIds.sort(bySlot);

		return new RuneBuild(
				new RuneTree(catalog.styleName(primaryStyleId), catalog.styleIconsById.get(primaryStyleId),
						primaryIds.stream().map(catalog::rune).toList()),
				new RuneTree(catalog.styleName(subStyleId), catalog.styleIconsById.get(subStyleId),
						subIds.stream().map(catalog::rune).toList()),
				shards);
	}

	/**
	 * ddragon 설명은 클라이언트 마크업이 섞여 온다({@code <b>}, {@code <font color>},
	 * {@code <lol-uikit-tooltipped-keyword>}, {@code <br>} 등 20여 종). 툴팁 한 칸용 평문으로 만든다.
	 */
	static String stripMarkup(String html) {
		if (html == null) {
			return null;
		}
		String text = html.replaceAll("<br\\s*/?>", " ")
				.replaceAll("<li>", " · ")
				.replaceAll("<[^>]+>", "")
				.replaceAll("\\s+", " ")
				.trim();
		return text.isEmpty() ? null : text;
	}

	private void ensureLoaded() {
		Instant now = Instant.now();
		if (!catalog.isEmpty() && Duration.between(loadedAt, now).toMinutes() < refreshMinutes) {
			return;
		}
		synchronized (this) {
			if (!catalog.isEmpty() && Duration.between(loadedAt, Instant.now()).toMinutes() < refreshMinutes) {
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

			Catalog loaded = Catalog.empty();
			for (JsonNode style : runeTrees) {
				int styleId = style.path("id").asInt(-1);
				if (styleId <= 0) {
					continue;
				}
				String styleName = style.path("name").asText("");
				if (!styleName.isBlank()) {
					loaded.styleNamesById.put(styleId, styleName);
				}
				String styleIcon = style.path("icon").asText("");
				if (!styleIcon.isBlank()) {
					loaded.styleIconsById.put(styleId, ICON_BASE + styleIcon);
				}

				int slotIndex = 0;
				for (JsonNode slot : style.path("slots")) {
					for (JsonNode rune : slot.path("runes")) {
						int runeId = rune.path("id").asInt(-1);
						if (runeId <= 0) {
							continue;
						}
						String runeName = rune.path("name").asText("");
						if (!runeName.isBlank()) {
							loaded.runeNamesById.put(runeId, runeName);
						}
						String runeIcon = rune.path("icon").asText("");
						if (!runeIcon.isBlank()) {
							loaded.runeIconsById.put(runeId, ICON_BASE + runeIcon);
						}
						loaded.runeDescriptionsById.put(runeId, stripMarkup(rune.path("shortDesc").asText(null)));
						loaded.runeStyleById.put(runeId, styleId);
						loaded.runeSlotById.put(runeId, slotIndex);
					}
					slotIndex++;
				}
			}

			this.catalog = loaded;
			this.loadedAt = Instant.now();
			log.info("Loaded rune metadata from Data Dragon: version={}, runeCount={}, styleCount={}",
					latestVersion, loaded.runeNamesById.size(), loaded.styleNamesById.size());
		} catch (Exception e) {
			log.warn("Failed to refresh rune metadata: {}", e.getMessage());
		}
	}

	/** 적재된 룬 메타 한 벌. 갱신 시 통째로 교체한다(volatile 참조 하나). */
	record Catalog(
			Map<Integer, String> styleNamesById,
			Map<Integer, String> styleIconsById,
			Map<Integer, String> runeNamesById,
			Map<Integer, String> runeIconsById,
			Map<Integer, String> runeDescriptionsById,
			Map<Integer, Integer> runeStyleById,
			Map<Integer, Integer> runeSlotById) {

		static Catalog empty() {
			return new Catalog(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
					new HashMap<>(), new HashMap<>(), new HashMap<>());
		}

		boolean isEmpty() {
			return runeNamesById.isEmpty();
		}

		String styleName(Integer styleId) {
			if (styleId == null) {
				return null;
			}
			return styleNamesById.getOrDefault(styleId, "스타일-" + styleId);
		}

		String runeName(int runeId) {
			Shard shard = STAT_SHARDS.get(runeId);
			if (shard != null) {
				return shard.label() == null ? shard.name() : shard.name() + " " + shard.label();
			}
			return runeNamesById.getOrDefault(runeId, "룬-" + runeId);
		}

		Rune rune(int runeId) {
			return new Rune(runeName(runeId), runeIconsById.get(runeId), runeDescriptionsById.get(runeId));
		}
	}

	/** 스코어보드 행에 쓰는 룬 아이콘 URL 쌍. 로드 실패·미매핑이면 각 필드가 null. */
	public record RuneIcons(String keystoneIconUrl, String subStyleIconUrl) {
	}

	public record RuneBuild(RuneTree primary, RuneTree sub, List<Shard> shards) {
	}

	public record RuneTree(String styleName, String styleIconUrl, List<Rune> runes) {
	}

	/** description 은 ddragon shortDesc 평문. 메타 미적재면 null. */
	public record Rune(String name, String iconUrl, String description) {
	}

	/** label 은 수치("+9", "+10~180"). 옛 패치 잔재 파편은 null. */
	public record Shard(String name, String iconUrl, String label) {
	}
}
