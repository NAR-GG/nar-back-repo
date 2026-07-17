package com.toy.nar.app.lolesports.stream;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChzzkLiveChannelResolverTest {

	/** HTTP 대신 채널별 방제를 주입하는 테스트 더블. */
	private ChzzkLiveChannelResolver resolverWithTitles(Map<String, String> titlesByChannel) {
		return new ChzzkLiveChannelResolver(null) {
			@Override
			protected String fetchLiveTitle(String channelId) {
				return titlesByChannel.get(channelId);
			}
		};
	}

	@Test
	void resolvesChannelWhoseTitleMentionsBothTeams() {
		// EWC 공식 채널B 실제 방제 형태: 더블헤더가 한 방제에 같이 들어온다.
		ChzzkLiveChannelResolver resolver = resolverWithTitles(Map.of(
				"3712674a199b9ce93e9476d59455110b", "[EN/SUB] 8강 2일차 | Dota 2 | EWC 2026",
				"2b753bd5325fc34bba16d66659c67aa2", "GEN vs JDG - DK vs BLG | 8강 | 리그오브레전드 | EWC 2026"));

		Optional<String> url = resolver.resolve("EWC", "DK", "Dplus Kia", "BLG", "Bilibili Gaming");

		assertThat(url).contains("https://chzzk.naver.com/live/2b753bd5325fc34bba16d66659c67aa2");
	}

	@Test
	void distinguishesSimultaneousMatchesByTitle() {
		// 동시 진행: 채널마다 다른 대진 방제 — 각 매치가 자기 채널로 배정돼야 한다.
		ChzzkLiveChannelResolver resolver = resolverWithTitles(Map.of(
				"2b753bd5325fc34bba16d66659c67aa2", "T1 vs HLE | 8강 | EWC 2026",
				"fce7c8735e0646e642007198a8875882", "GEN vs JDG | 8강 | EWC 2026"));

		assertThat(resolver.resolve("EWC", "T1", "T1", "HLE", "Hanwha Life Esports"))
				.contains("https://chzzk.naver.com/live/2b753bd5325fc34bba16d66659c67aa2");
		assertThat(resolver.resolve("EWC", "GEN", "Gen.g", "JDG", "Beijing Jdg Esports"))
				.contains("https://chzzk.naver.com/live/fce7c8735e0646e642007198a8875882");
	}

	@Test
	void emptyWhenNoChannelMentionsBothTeams() {
		ChzzkLiveChannelResolver resolver = resolverWithTitles(Map.of(
				"2b753bd5325fc34bba16d66659c67aa2", "T1 vs HLE | 8강 | EWC 2026"));

		assertThat(resolver.resolve("EWC", "GEN", "Gen.g", "JDG", "Beijing Jdg Esports")).isEmpty();
	}

	@Test
	void teamCodeDoesNotMatchInsideLongerToken() {
		// "T1"이 "AT10" 같은 토큰 내부에 걸리면 오배정 — 영숫자 경계 매칭이어야 한다.
		ChzzkLiveChannelResolver resolver = resolverWithTitles(Map.of(
				"2b753bd5325fc34bba16d66659c67aa2", "AT10 vs HLE | EWC 2026"));

		assertThat(resolver.resolve("EWC", "T1", "T1", "HLE", "Hanwha Life Esports")).isEmpty();
	}

	@Test
	void fallsBackToTeamNameWhenCodeMissing() {
		ChzzkLiveChannelResolver resolver = resolverWithTitles(Map.of(
				"2b753bd5325fc34bba16d66659c67aa2", "Hanwha vs Dplus | EWC 2026"));

		assertThat(resolver.resolve("EWC", null, "Hanwha Life Esports", null, "Dplus Kia"))
				.contains("https://chzzk.naver.com/live/2b753bd5325fc34bba16d66659c67aa2");
	}

	@Test
	void unknownLeagueResolvesEmpty() {
		ChzzkLiveChannelResolver resolver = resolverWithTitles(Map.of());

		assertThat(resolver.resolve("LPL", "BLG", "Bilibili Gaming", "JDG", "JDG")).isEmpty();
	}
}
