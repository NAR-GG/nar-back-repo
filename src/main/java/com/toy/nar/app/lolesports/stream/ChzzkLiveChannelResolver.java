package com.toy.nar.app.lolesports.stream;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 치지직 후보 채널들의 라이브 방제를 조회해 "이 매치를 어느 채널이 중계 중인지" 판별한다.
 * 같은 리그가 여러 채널에서 동시에 다른 경기를 중계하는 케이스(EWC 공식 A~F, LCK 동시 진행)용.
 * 방제에 양 팀 코드가 모두 등장하는 채널을 그 매치의 스트림으로 배정하고, 못 찾으면 empty —
 * 호출부가 기존 정적 링크로 폴백하므로 최악이어도 동작은 지금과 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChzzkLiveChannelResolver {

	/**
	 * 리그별 후보 채널 풀(치지직 channelId). 채널-경기 편성이 유동적인 리그만 등록한다.
	 * EWC: 공식 채널 A~F (2026-07-18 채널B가 LoL 방제로 검증됨 — 편성 변경 대비 전부 후보).
	 * LCK: 공식 채널. 동시 진행 시 쓰는 제2채널 ID 확인되면 여기에 추가하면 끝.
	 */
	private static final Map<String, List<String>> CANDIDATE_CHANNELS = Map.of(
			"EWC", List.of(
					"fce7c8735e0646e642007198a8875882",
					"2b753bd5325fc34bba16d66659c67aa2",
					"d3a2be189374a4f07895137f8a55397c",
					"3712674a199b9ce93e9476d59455110b",
					"e2add8c4a629d61575761e9b5396170b",
					"e5edff2dc0e0012618e3812595b9f463"),
			"LCK", List.of(
					"9381e7d6816e6d915a44a13c0195b202"));

	/** 방제 캐시 TTL. 디스커버리(10초)·스케줄 동기화가 반복 호출해도 채널당 이 주기로만 외부 호출. */
	private static final long CACHE_TTL_MS = 30_000L;

	private final WebClient webClient;

	private record CachedTitle(String title, long fetchedAtMs) {
	}

	private final Map<String, CachedTitle> titleCache = new ConcurrentHashMap<>();

	/**
	 * 리그 후보 채널 중 방제에 양 팀이 모두 등장하는 채널의 시청 URL. 없으면 empty.
	 * 팀 판별은 코드(word-boundary) 우선, 코드가 없으면 팀명 첫 토큰 부분일치.
	 */
	public Optional<String> resolve(
			String leagueName, String blueCode, String blueName, String redCode, String redName) {
		if (leagueName == null) {
			return Optional.empty();
		}
		List<String> candidates = CANDIDATE_CHANNELS.getOrDefault(
				leagueName.trim().toUpperCase(Locale.ROOT), List.of());
		for (String channelId : candidates) {
			String title = cachedLiveTitle(channelId);
			if (title != null && mentions(title, blueCode, blueName) && mentions(title, redCode, redName)) {
				return Optional.of("https://chzzk.naver.com/live/" + channelId);
			}
		}
		return Optional.empty();
	}

	private String cachedLiveTitle(String channelId) {
		long now = System.currentTimeMillis();
		CachedTitle cached = titleCache.get(channelId);
		if (cached != null && now - cached.fetchedAtMs() < CACHE_TTL_MS) {
			return cached.title();
		}
		String title = fetchLiveTitle(channelId);
		// 실패/방송 종료(null)도 캐시해 후보 채널 전체를 매 호출 재조회하지 않는다.
		titleCache.put(channelId, new CachedTitle(title, now));
		return title;
	}

	/** 치지직 공개 API 로 현재 라이브 방제 조회. 방송 중이 아니거나 실패하면 null. 테스트에서 오버라이드. */
	protected String fetchLiveTitle(String channelId) {
		try {
			JsonNode root = webClient.get()
					.uri(uri -> uri
							.scheme("https")
							.host("api.chzzk.naver.com")
							.path("/service/v2/channels/{channelId}/live-detail")
							.build(channelId))
					.header("User-Agent", "Mozilla/5.0")
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
			if (root == null) {
				return null;
			}
			JsonNode content = root.path("content");
			if (!"OPEN".equalsIgnoreCase(content.path("status").asText(""))) {
				return null;
			}
			String title = content.path("liveTitle").asText("");
			return title.isBlank() ? null : title;
		} catch (Exception e) {
			log.debug("치지직 라이브 방제 조회 실패 channelId={}: {}", channelId, e.getMessage());
			return null;
		}
	}

	/** 방제에 팀이 등장하는지. 코드는 영숫자 경계 매칭("T1"이 "AT1"에 걸리지 않게), 팀명은 첫 토큰(3자+) 부분일치. */
	private boolean mentions(String title, String teamCode, String teamName) {
		if (teamCode != null && teamCode.trim().length() >= 2) {
			Pattern pattern = Pattern.compile(
					"(?<![A-Za-z0-9])" + Pattern.quote(teamCode.trim()) + "(?![A-Za-z0-9])",
					Pattern.CASE_INSENSITIVE);
			if (pattern.matcher(title).find()) {
				return true;
			}
		}
		if (teamName != null && !teamName.isBlank()) {
			String firstToken = teamName.trim().split("\\s+")[0];
			if (firstToken.length() >= 3
					&& title.toLowerCase(Locale.ROOT).contains(firstToken.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}
}
