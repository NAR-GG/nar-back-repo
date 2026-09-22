package com.toy.nar.app.lolesports;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LeagueConstantsTest {

	@Test
	void EWC_slug은_내부_리그명으로_보정된다() {
		assertThat(LeagueConstants.fromApiSlug("ewc_lol")).isEqualTo("EWC");
	}

	@Test
	void 일반_리그_slug은_대문자화만_한다() {
		assertThat(LeagueConstants.fromApiSlug("lck")).isEqualTo("LCK");
		assertThat(LeagueConstants.fromApiSlug("first_stand")).isEqualTo("FIRST_STAND");
		assertThat(LeagueConstants.fromApiSlug(null)).isEmpty();
	}

	@Test
	void EWC는_전용_라이브_스트림_URL을_가진다() {
		// 폴백(LCK aflol)이 아닌 EWC 전용 URL이어야 라이브에 올바른 방송이 뜬다.
		assertThat(LeagueConstants.getLiveStreamUrl("EWC")).isNotEqualTo(LeagueConstants.DEFAULT_STREAM_URL);
		assertThat(LeagueConstants.getStreamLinks("EWC")).isNotEmpty();
	}

	@Test
	void 아시안게임은_세_목록에_모두_등록된다() {
		// 셋 중 하나라도 빠지면 조용히 반쯤 동작한다 — 동기화는 도는데 필터에 안 뜨거나,
		// 필터엔 뜨는데 리그 ID 가 없어 조회가 비거나.
		assertThat(LeagueConstants.TARGET_LEAGUES).contains("ASIAN_GAMES");
		assertThat(LeagueConstants.ALLOWED_LEAGUES).contains("ASIAN_GAMES");
		assertThat(LeagueConstants.LEAGUE_IDS).containsEntry("ASIAN_GAMES", "117228885404001005");
		assertThat(LeagueConstants.fromApiSlug("asian_games")).isEqualTo("ASIAN_GAMES");
	}

	@Test
	void 아시안게임은_중계처가_없어_SOOP_폴백을_막는다() {
		// OCA 주최라 Riot 계열 채널이 없다. 폴백이 걸리면 엉뚱한 LCK 방송으로 보낸다.
		assertThat(LeagueConstants.getLiveStreamUrl("ASIAN_GAMES")).isNull();
		assertThat(LeagueConstants.getStreamLinks("ASIAN_GAMES")).isEmpty();
	}

	@Test
	void KeSPA는_스트림_링크가_없고_SOOP_폴백도_안_한다() {
		// KeSPA Cup 은 Disney+ 독점 — 앱에 노출할 대체 채널이 없다. SOOP 폴백도 금지.
		assertThat(LeagueConstants.getStreamLinks("KESPA")).isEmpty();
		assertThat(LeagueConstants.getLiveStreamUrl("KESPA")).isNull();
	}
}
