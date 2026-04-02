package com.toy.nar.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameNormalizerTest {

	@Test
	void normalizeTeamName_mapsDrxBrandingToKiwoomDrx() {
		assertThat(NameNormalizer.normalizeTeamName("DRX")).isEqualTo("Kiwoom Drx");
		assertThat(NameNormalizer.normalizeTeamName("KIWOOM DRX")).isEqualTo("Kiwoom Drx");
	}

	@Test
	void normalizeTeamName_mapsDrxChallengersBrandingToKiwoomDrxChallengers() {
		assertThat(NameNormalizer.normalizeTeamName("DRX Challengers")).isEqualTo("Kiwoom Drx Challengers");
		assertThat(NameNormalizer.normalizeTeamName("KIWOOM DRX CHALLENGERS")).isEqualTo("Kiwoom Drx Challengers");
	}
}
