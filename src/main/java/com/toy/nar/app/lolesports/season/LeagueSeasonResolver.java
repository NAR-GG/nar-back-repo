package com.toy.nar.app.lolesports.season;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.lolesports.LeagueConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * lolesports 토너먼트 기간(getTournamentsForLeague)을 기준으로
 * 매치 일시 → 시즌(연도/스플릿)을 해석한다. 외부 API 실패 시 빈 결과를 돌려주고 동기화를 막지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeagueSeasonResolver {

	private static final Duration CACHE_TTL = Duration.ofHours(6);
	private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");
	private static final Map<String, String> SPLIT_KEYWORDS = Map.ofEntries(
			Map.entry("spring", "Spring"),
			Map.entry("summer", "Summer"),
			Map.entry("winter", "Winter"),
			Map.entry("fall", "Fall"),
			Map.entry("autumn", "Fall"),
			Map.entry("msi", "MSI"),
			Map.entry("worlds", "Worlds"),
			Map.entry("first_stand", "First Stand"),
			Map.entry("firststand", "First Stand"),
			Map.entry("cup", "Cup"),
			Map.entry("playoffs", "Playoffs"),
			Map.entry("regional", "Regional"));
	private static final int MAX_SPLIT_LENGTH = 20;

	private final WebClient webClient;

	@Value("${lolesports.riot-api.key}")
	private String riotApiKey;

	private final Map<String, CachedWindows> cacheByLeague = new ConcurrentHashMap<>();

	public record LeagueSeason(Integer year, String split) {
	}

	public record SeasonWindow(String tournamentSlug, LocalDate startDate, LocalDate endDate, LeagueSeason season) {
		public long durationDays() {
			return ChronoUnit.DAYS.between(startDate, endDate);
		}
	}

	public Optional<LeagueSeason> resolve(String leagueName, LocalDateTime matchDateUtc) {
		if (leagueName == null || matchDateUtc == null) {
			return Optional.empty();
		}
		LocalDate matchDay = matchDateUtc.toLocalDate();
		// 기간이 겹치면 더 짧은(더 구체적인) 토너먼트를 우선한다
		return windowsFor(leagueName).stream()
				.filter(window -> !matchDay.isBefore(window.startDate()) && !matchDay.isAfter(window.endDate()))
				.min(Comparator.comparingLong(SeasonWindow::durationDays))
				.map(SeasonWindow::season);
	}

	public List<SeasonWindow> windowsFor(String leagueName) {
		String normalized = leagueName.trim().toUpperCase(Locale.ROOT);
		CachedWindows cached = cacheByLeague.get(normalized);
		if (cached != null && cached.fetchedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
			return cached.windows();
		}
		List<SeasonWindow> windows = fetchWindows(normalized);
		if (!windows.isEmpty()) {
			cacheByLeague.put(normalized, new CachedWindows(Instant.now(), windows));
		}
		return windows;
	}

	private List<SeasonWindow> fetchWindows(String leagueName) {
		String leagueId = LeagueConstants.LEAGUE_IDS.get(leagueName);
		if (leagueId == null) {
			return List.of();
		}
		try {
			JsonNode root = webClient.get()
					.uri(uri -> uri
							.scheme("https")
							.host("esports-api.lolesports.com")
							.path("/persisted/gw/getTournamentsForLeague")
							.queryParam("hl", "ko-KR")
							.queryParam("leagueId", leagueId)
							.build())
					.header("x-api-key", riotApiKey)
					.header("Referer", "https://lolesports.com/")
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
			return parseWindows(leagueName, root);
		} catch (Exception e) {
			log.warn("리그 토너먼트 조회 실패 - 시즌 해석을 건너뜁니다: {}", leagueName, e);
			return List.of();
		}
	}

	private List<SeasonWindow> parseWindows(String leagueName, JsonNode root) {
		if (root == null) {
			return List.of();
		}
		List<SeasonWindow> windows = new ArrayList<>();
		for (JsonNode league : root.path("data").path("leagues")) {
			for (JsonNode tournament : league.path("tournaments")) {
				String slug = tournament.path("slug").asText("");
				LocalDate start = parseDate(tournament.path("startDate").asText(null));
				LocalDate end = parseDate(tournament.path("endDate").asText(null));
				if (slug.isBlank() || start == null || end == null) {
					continue;
				}
				LeagueSeason season = parseSeason(leagueName, slug, start);
				if (season != null) {
					windows.add(new SeasonWindow(slug, start, end, season));
				}
			}
		}
		return List.copyOf(windows);
	}

	private LocalDate parseDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * 토너먼트 슬러그에서 시즌을 파싱한다. 예: "lck_spring_2026" → (2026, "Spring").
	 * 알려진 키워드가 없으면 리그/연도 토큰을 제외한 나머지를 정리해 스플릿명으로 쓰고,
	 * 남는 토큰이 없으면 "Season"으로 둔다.
	 */
	static LeagueSeason parseSeason(String leagueName, String slug, LocalDate startDate) {
		String lowerSlug = slug.toLowerCase(Locale.ROOT);
		Integer year = null;
		Matcher yearMatcher = YEAR_PATTERN.matcher(lowerSlug);
		if (yearMatcher.find()) {
			year = Integer.parseInt(yearMatcher.group(1));
		} else if (startDate != null) {
			year = startDate.getYear();
		}
		if (year == null) {
			return null;
		}

		for (Map.Entry<String, String> keyword : SPLIT_KEYWORDS.entrySet()) {
			if (lowerSlug.contains(keyword.getKey())) {
				return new LeagueSeason(year, keyword.getValue());
			}
		}

		String leagueToken = leagueName == null ? "" : leagueName.toLowerCase(Locale.ROOT);
		StringBuilder remainder = new StringBuilder();
		for (String token : lowerSlug.split("_")) {
			if (token.isBlank() || token.equals(leagueToken) || token.matches("20\\d{2}")) {
				continue;
			}
			if (!remainder.isEmpty()) {
				remainder.append(' ');
			}
			remainder.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
		}
		String split = remainder.isEmpty() ? "Season" : remainder.toString();
		if (split.length() > MAX_SPLIT_LENGTH) {
			split = split.substring(0, MAX_SPLIT_LENGTH).trim();
		}
		return new LeagueSeason(year, split);
	}

	private record CachedWindows(Instant fetchedAt, List<SeasonWindow> windows) {
	}
}
