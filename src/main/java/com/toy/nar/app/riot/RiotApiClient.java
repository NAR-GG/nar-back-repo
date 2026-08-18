package com.toy.nar.app.riot;

import com.toy.nar.app.riot.dto.RiotAccountResolveResponse;
import com.toy.nar.app.riot.dto.RiotCurrentGameResponse;
import com.toy.nar.app.riot.dto.RiotLeagueEntryResponse;
import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.app.riot.dto.RiotSummonerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class RiotApiClient {

	private static final String RANKED_SOLO_QUEUE_TYPE = "RANKED_SOLO_5x5";

	private final WebClient webClient;
	private final RiotApiProperties riotApiProperties;

	public void assertConfigured() {
		ensureConfigured();
	}

	public RiotAccountResolveResponse resolveAccountByRiotId(String gameName, String tagLine) {
		ensureConfigured();
		URI uri = URI.create(riotApiProperties.getRegionalBaseUrl()
				+ "/riot/account/v1/accounts/by-riot-id/"
				+ encodePathSegment(gameName)
				+ "/"
				+ encodePathSegment(tagLine));
		return getRequired(uri, RiotAccountResolveResponse.class, "Riot account resolve failed");
	}

	/**
	 * 플랫폼별 소환사 조회. 해당 플랫폼에 계정이 없으면 404 → empty.
	 * PUUID 해석(account-v1)은 글로벌이라 지역 무관이지만 소환사 정보는 플랫폼 호스트로 물어야 한다.
	 */
	public Optional<RiotSummonerResponse> findSummonerByPuuid(String puuid, String platform) {
		ensureConfigured();
		URI uri = URI.create(RiotPlatform.apiHost(platform)
				+ "/lol/summoner/v4/summoners/by-puuid/"
				+ encodePathSegment(puuid));
		return getOptional(uri, RiotSummonerResponse.class, "Riot summoner resolve failed");
	}

	/** 솔로 랭크(RANKED_SOLO_5x5) 랭크 정보. 언랭이거나 해당 플랫폼에 기록이 없으면 empty. */
	public Optional<RiotLeagueEntryResponse> findSoloRankEntry(String puuid, String platform) {
		ensureConfigured();
		URI uri = URI.create(RiotPlatform.apiHost(platform)
				+ "/lol/league/v4/entries/by-puuid/"
				+ encodePathSegment(puuid));
		return getOptional(uri, RiotLeagueEntryResponse[].class, "Riot league entries fetch failed")
				.stream()
				.flatMap(Arrays::stream)
				.filter(entry -> RANKED_SOLO_QUEUE_TYPE.equals(entry.queueType()))
				.findFirst();
	}

	public List<String> getRecentMatchIdsByPuuid(String puuid, int count) {
		ensureConfigured();
		URI uri = URI.create(riotApiProperties.getRegionalBaseUrl()
				+ "/lol/match/v5/matches/by-puuid/"
				+ encodePathSegment(puuid)
				+ "/ids?start=0&count="
				+ count);
		String[] response = getRequired(uri, String[].class, "Riot match list fetch failed");
		return Arrays.asList(response);
	}

	/** 최근 솔로 랭크(queue=420) 매치 ID 목록. match-v5는 스트리머 모드 필터 대상이 아니다. */
	public List<String> getRecentSoloRankMatchIdsByPuuid(String puuid, int count, String platform) {
		ensureConfigured();
		URI uri = URI.create(RiotPlatform.regionalHost(platform)
				+ "/lol/match/v5/matches/by-puuid/"
				+ encodePathSegment(puuid)
				+ "/ids?queue=420&start=0&count="
				+ count);
		String[] response = getRequired(uri, String[].class, "Riot solo rank match list fetch failed");
		return Arrays.asList(response);
	}

	/** 매치 상세(플랫폼별 지역 라우팅). 완료 매치 폴백 감지용. */
	public RiotMatchResponse getMatch(String matchId, String platform) {
		ensureConfigured();
		URI uri = URI.create(RiotPlatform.regionalHost(platform)
				+ "/lol/match/v5/matches/"
				+ encodePathSegment(matchId));
		return getRequired(uri, RiotMatchResponse.class, "Riot match fetch failed");
	}

	public RiotMatchResponse getMatch(String matchId) {
		ensureConfigured();
		URI uri = URI.create(riotApiProperties.getRegionalBaseUrl()
				+ "/lol/match/v5/matches/"
				+ encodePathSegment(matchId));
		return getRequired(uri, RiotMatchResponse.class, "Riot match fetch failed");
	}

	public Optional<RiotCurrentGameResponse> getActiveGameByPuuid(String puuid, String platform) {
		ensureConfigured();
		URI uri = URI.create(RiotPlatform.apiHost(platform)
				+ "/lol/spectator/v5/active-games/by-summoner/"
				+ encodePathSegment(puuid));
		return getOptional(uri, RiotCurrentGameResponse.class, "Failed to fetch current game by puuid");
	}

	public Optional<RiotCurrentGameResponse> getActiveGameBySummonerId(String summonerId) {
		ensureConfigured();
		URI uri = URI.create(riotApiProperties.getKrBaseUrl()
				+ "/lol/spectator/v4/active-games/by-summoner/"
				+ encodePathSegment(summonerId));
		return getOptional(uri, RiotCurrentGameResponse.class, "Failed to fetch current game by summonerId");
	}

	private <T> T getRequired(URI uri, Class<T> responseType, String errorMessage) {
		try {
			T response = webClient.get()
					.uri(uri)
					.header(HttpHeaders.ACCEPT, "application/json")
					.header("X-Riot-Token", riotApiProperties.getKey())
					.retrieve()
					.bodyToMono(responseType)
					.block(Duration.ofMillis(riotApiProperties.getRequestTimeoutMs()));
			if (response == null) {
				throw new RiotApiException(errorMessage + ": empty response", 500);
			}
			return response;
		} catch (WebClientResponseException e) {
			throw new RiotApiException(errorMessage + ": " + e.getResponseBodyAsString(), e.getStatusCode().value(), e);
		} catch (RiotApiException e) {
			throw e;
		} catch (Exception e) {
			throw new RiotApiException(errorMessage, 500, e);
		}
	}

	private <T> Optional<T> getOptional(URI uri, Class<T> responseType, String errorMessage) {
		try {
			Optional<T> response = webClient.get()
					.uri(uri)
					.header(HttpHeaders.ACCEPT, "application/json")
					.header("X-Riot-Token", riotApiProperties.getKey())
					.exchangeToMono(clientResponse -> {
						HttpStatusCode statusCode = clientResponse.statusCode();
						if (statusCode.is2xxSuccessful()) {
							return clientResponse.bodyToMono(responseType)
									.map(Optional::<T>of);
						}
						if (statusCode.value() == 404) {
							// 스트리머 모드 계정은 spectator-v5 가 404 "filtered" 를 준다(Riot 2025-10
							// 익명성 정책). "지금 게임 중이 아님" 404 와 코드가 같아 지금은 구분이 안 되고,
							// 그래서 라이브 감지가 영영 안 되는 계정이 몇 개인지도 모른다. 본문을 남겨
							// 명단을 확보한다 — 폴백을 켤지 판단하는 근거다. 반환값은 그대로 empty 라
							// 동작은 바뀌지 않는다.
							return clientResponse.bodyToMono(String.class)
									.defaultIfEmpty("")
									.map(body -> {
										if (body.contains("filtered")) {
											log.info("[riot-404-filtered] {} body={}", uri, body);
										}
										return Optional.<T>empty();
									});
						}
						return clientResponse.createException().flatMap(Mono::error);
					})
					.block(Duration.ofMillis(riotApiProperties.getRequestTimeoutMs()));
			return response == null ? Optional.empty() : response;
		} catch (WebClientResponseException e) {
			throw new RiotApiException(errorMessage + ": " + e.getResponseBodyAsString(), e.getStatusCode().value(), e);
		} catch (RiotApiException e) {
			throw e;
		} catch (Exception e) {
			throw new RiotApiException(errorMessage, 500, e);
		}
	}

	private void ensureConfigured() {
		if (!riotApiProperties.isEnabled() || riotApiProperties.getKey() == null || riotApiProperties.getKey().isBlank()) {
			throw new RiotApiException(
					"Riot API is not configured. Set RIOT_API_ENABLED=true and provide RIOT_API_KEY.",
					500);
		}
	}

	private String encodePathSegment(String value) {
		String normalized = value == null ? null : value.trim();
		if (normalized == null || normalized.isBlank()) {
			throw new RiotApiException("Riot API path parameter must not be blank", 400);
		}
		return org.springframework.web.util.UriUtils.encodePathSegment(normalized, java.nio.charset.StandardCharsets.UTF_8);
	}
}
