package com.toy.nar.app.riot;

import com.toy.nar.app.riot.dto.RiotAccountResolveResponse;
import com.toy.nar.app.riot.dto.RiotCurrentGameResponse;
import com.toy.nar.app.riot.dto.RiotMatchResponse;
import com.toy.nar.app.riot.dto.RiotSummonerResponse;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class RiotApiClient {

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

	public RiotSummonerResponse getSummonerByPuuid(String puuid) {
		ensureConfigured();
		URI uri = URI.create(riotApiProperties.getKrBaseUrl()
				+ "/lol/summoner/v4/summoners/by-puuid/"
				+ encodePathSegment(puuid));
		return getRequired(uri, RiotSummonerResponse.class, "Riot summoner resolve failed");
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

	public RiotMatchResponse getMatch(String matchId) {
		ensureConfigured();
		URI uri = URI.create(riotApiProperties.getRegionalBaseUrl()
				+ "/lol/match/v5/matches/"
				+ encodePathSegment(matchId));
		return getRequired(uri, RiotMatchResponse.class, "Riot match fetch failed");
	}

	public Optional<RiotCurrentGameResponse> getActiveGameByPuuid(String puuid) {
		ensureConfigured();
		URI uri = URI.create(riotApiProperties.getKrBaseUrl()
				+ "/lol/spectator/v5/active-games/by-puuid/"
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
							return Mono.just(Optional.<T>empty());
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
		return org.springframework.web.util.UriUtils.encodePathSegment(value, java.nio.charset.StandardCharsets.UTF_8);
	}
}
