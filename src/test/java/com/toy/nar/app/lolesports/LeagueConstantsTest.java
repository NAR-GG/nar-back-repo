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
}
