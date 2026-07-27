package com.toy.nar.app.riot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiotPlatformTest {

	@Test
	void mapsRegionTagToPlatformRoutingValue() {
		assertThat(RiotPlatform.toPlatform("KR")).isEqualTo("KR");
		assertThat(RiotPlatform.toPlatform("EUW")).isEqualTo("EUW1");
		assertThat(RiotPlatform.toPlatform("na")).isEqualTo("NA1");
		assertThat(RiotPlatform.toPlatform("EUNE")).isEqualTo("EUN1");
	}

	@Test
	void passesThroughAlreadyRoutingValueAndDefaultsBlankToKr() {
		assertThat(RiotPlatform.toPlatform("EUW1")).isEqualTo("EUW1");
		assertThat(RiotPlatform.toPlatform(null)).isEqualTo("KR");
		assertThat(RiotPlatform.toPlatform("  ")).isEqualTo("KR");
	}

	@Test
	void buildsPlatformApiHost() {
		assertThat(RiotPlatform.apiHost("KR")).isEqualTo("https://kr.api.riotgames.com");
		assertThat(RiotPlatform.apiHost("EUW")).isEqualTo("https://euw1.api.riotgames.com");
		assertThat(RiotPlatform.apiHost("NA1")).isEqualTo("https://na1.api.riotgames.com");
		assertThat(RiotPlatform.apiHost(null)).isEqualTo("https://kr.api.riotgames.com");
	}

	@Test
	void mapsToOpggRegionCode() {
		assertThat(RiotPlatform.opggRegion("KR")).isEqualTo("kr");
		assertThat(RiotPlatform.opggRegion("EUW1")).isEqualTo("euw");
		assertThat(RiotPlatform.opggRegion("EUW")).isEqualTo("euw");
		assertThat(RiotPlatform.opggRegion("NA1")).isEqualTo("na");
	}
}
