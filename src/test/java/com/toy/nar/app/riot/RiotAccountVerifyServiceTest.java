package com.toy.nar.app.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toy.nar.app.riot.dto.RiotAccountResolveResponse;
import com.toy.nar.app.riot.dto.RiotAccountVerification;
import com.toy.nar.app.riot.dto.RiotLeagueEntryResponse;
import com.toy.nar.app.riot.dto.RiotSummonerResponse;

@ExtendWith(MockitoExtension.class)
class RiotAccountVerifyServiceTest {

	@Mock
	private RiotApiClient riotApiClient;

	@InjectMocks
	private RiotAccountVerifyService service;

	@Test
	@DisplayName("계정·소환사·솔랭이 다 있으면 레벨·티어·op.gg URL을 채워 반환한다")
	void verifiesFully() {
		when(riotApiClient.resolveAccountByRiotId("Chop Chop Butter", "CUP"))
				.thenReturn(new RiotAccountResolveResponse("puuid-1", "Chop Chop Butter", "CUP"));
		when(riotApiClient.findSummonerByPuuid("puuid-1", "KR"))
				.thenReturn(Optional.of(new RiotSummonerResponse("sid", "puuid-1", 1171, 1L)));
		when(riotApiClient.findSoloRankEntry("puuid-1", "KR"))
				.thenReturn(Optional.of(new RiotLeagueEntryResponse(
						"RANKED_SOLO_5x5", "CHALLENGER", "I", 2003, 106, 66)));

		RiotAccountVerification result = service.verify("Chop Chop Butter#CUP", "KR");

		assertThat(result.accountFound()).isTrue();
		assertThat(result.summonerFound()).isTrue();
		assertThat(result.puuid()).isEqualTo("puuid-1");
		assertThat(result.summonerLevel()).isEqualTo(1171);
		assertThat(result.soloTier()).isEqualTo("CHALLENGER");
		assertThat(result.soloLeaguePoints()).isEqualTo(2003);
		assertThat(result.opggUrl()).isEqualTo("https://www.op.gg/summoners/kr/Chop+Chop+Butter-CUP");
		assertThat(result.message()).isNull();
	}

	@Test
	@DisplayName("region이 NA면 NA1 플랫폼으로 조회한다")
	void routesByRegion() {
		when(riotApiClient.resolveAccountByRiotId("FLY Quad", "123"))
				.thenReturn(new RiotAccountResolveResponse("puuid-2", "FLY Quad", "123"));
		when(riotApiClient.findSummonerByPuuid("puuid-2", "NA1"))
				.thenReturn(Optional.of(new RiotSummonerResponse("sid", "puuid-2", 127, 1L)));
		when(riotApiClient.findSoloRankEntry("puuid-2", "NA1")).thenReturn(Optional.empty());

		RiotAccountVerification result = service.verify("FLY Quad#123", "NA");

		assertThat(result.platform()).isEqualTo("NA1");
		assertThat(result.soloTier()).isNull();
		assertThat(result.message()).contains("언랭");
		assertThat(result.opggUrl()).contains("/summoners/na/");
	}

	@Test
	@DisplayName("Riot ID가 없으면(404) 예외 대신 accountFound=false로 내린다")
	void reportsMissingAccount() {
		when(riotApiClient.resolveAccountByRiotId("nope", "0000"))
				.thenThrow(new RiotApiException("not found", 404));

		RiotAccountVerification result = service.verify("nope#0000", null);

		assertThat(result.accountFound()).isFalse();
		assertThat(result.platform()).isEqualTo("KR"); // region 미지정 → KR
		assertThat(result.message()).contains("오타");
		verify(riotApiClient, never()).findSummonerByPuuid("puuid", "KR");
	}

	@Test
	@DisplayName("계정은 있으나 해당 플랫폼에 소환사가 없으면 지역 오지정으로 안내한다")
	void reportsWrongPlatform() {
		when(riotApiClient.resolveAccountByRiotId("Chop Chop Butter", "CUP"))
				.thenReturn(new RiotAccountResolveResponse("puuid-1", "Chop Chop Butter", "CUP"));
		when(riotApiClient.findSummonerByPuuid("puuid-1", "NA1")).thenReturn(Optional.empty());

		RiotAccountVerification result = service.verify("Chop Chop Butter#CUP", "NA");

		assertThat(result.accountFound()).isTrue();
		assertThat(result.summonerFound()).isFalse();
		assertThat(result.message()).contains("NA1");
		verify(riotApiClient, never()).findSoloRankEntry("puuid-1", "NA1");
	}

	@Test
	@DisplayName("riotId 형식이 틀리면 IllegalArgumentException")
	void rejectsMalformedRiotId() {
		assertThatThrownBy(() -> service.verify("태그없음", "KR"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("404가 아닌 Riot 오류(429 등)는 그대로 올린다")
	void propagatesNonNotFoundErrors() {
		when(riotApiClient.resolveAccountByRiotId("a", "b"))
				.thenThrow(new RiotApiException("rate limited", 429));

		assertThatThrownBy(() -> service.verify("a#b", "KR"))
				.isInstanceOf(RiotApiException.class);
	}
}
