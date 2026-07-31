package com.toy.nar.app.riot;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.toy.nar.app.riot.dto.RiotAccountVerification;
import com.toy.nar.app.riot.dto.RiotLeagueEntryResponse;
import com.toy.nar.app.riot.dto.RiotSummonerResponse;

import lombok.RequiredArgsConstructor;

/**
 * 백오피스에서 선수 계정을 넣기 전에 riotId·지역이 맞는지 확인하는 읽기 전용 검증.
 * 저장은 하지 않는다 — 부착은 {@code POST /players/{id}/solo-rank-account}가 담당한다.
 *
 * <p>Riot 호출 3회(account-v1 → summoner-v4 → league-v4)이고 실패 사유별로 결과를 나눠 담는다.
 * 예외를 그대로 올리면 FE가 "오타"와 "지역 오지정"을 구분할 수 없어서, 404는 예외 대신 플래그로 내린다.
 */
@Service
@RequiredArgsConstructor
public class RiotAccountVerifyService {

	private final RiotApiClient riotApiClient;

	public RiotAccountVerification verify(String riotId, String region) {
		RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse(riotId)
				.orElseThrow(() -> new IllegalArgumentException("riotId는 '이름#태그' 형식이어야 합니다: " + riotId));
		String platform = RiotPlatform.toPlatform(region);

		String puuid;
		String gameName;
		String tagLine;
		try {
			var account = riotApiClient.resolveAccountByRiotId(parsed.gameName(), parsed.tagLine());
			puuid = account.puuid();
			gameName = account.gameName();
			tagLine = account.tagLine();
		} catch (RiotApiException e) {
			if (e.getStatusCode() == 404) {
				return notFound(parsed.normalizedRiotId(), platform,
						"해당 Riot ID의 계정이 없습니다. 이름·태그 오타를 확인하세요.");
			}
			throw e;
		}

		Optional<RiotSummonerResponse> summoner = riotApiClient.findSummonerByPuuid(puuid, platform);
		if (summoner.isEmpty()) {
			// 계정 자체는 있으나 이 플랫폼에 소환사 기록이 없다 → 지역 선택이 틀렸을 가능성이 높다.
			return new RiotAccountVerification(true, false,
					parsed.normalizedRiotId(), gameName, tagLine, puuid, platform,
					null, null, null, null, null, null,
					RiotPlatform.opggUrl(gameName, tagLine, platform),
					"계정은 있지만 " + platform + " 지역에 기록이 없습니다. 지역을 확인하세요.");
		}

		Optional<RiotLeagueEntryResponse> solo = riotApiClient.findSoloRankEntry(puuid, platform);
		return new RiotAccountVerification(true, true,
				parsed.normalizedRiotId(), gameName, tagLine, puuid, platform,
				summoner.get().summonerLevel(),
				solo.map(RiotLeagueEntryResponse::tier).orElse(null),
				solo.map(RiotLeagueEntryResponse::rank).orElse(null),
				solo.map(RiotLeagueEntryResponse::leaguePoints).orElse(null),
				solo.map(RiotLeagueEntryResponse::wins).orElse(null),
				solo.map(RiotLeagueEntryResponse::losses).orElse(null),
				RiotPlatform.opggUrl(gameName, tagLine, platform),
				solo.isEmpty() ? "솔로 랭크 기록이 없습니다(언랭)." : null);
	}

	private RiotAccountVerification notFound(String riotId, String platform, String message) {
		return new RiotAccountVerification(false, false, riotId, null, null, null, platform,
				null, null, null, null, null, null, "", message);
	}
}
