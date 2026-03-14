package com.toy.nar.app.riot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiotIdParserTest {

	@Test
	void parsesRiotIdWithSpaceBeforeHash() {
		RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse("Peyz #KR11")
				.orElseThrow();

		assertThat(parsed.gameName()).isEqualTo("Peyz");
		assertThat(parsed.tagLine()).isEqualTo("KR11");
		assertThat(parsed.normalizedRiotId()).isEqualTo("Peyz#KR11");
	}

	@Test
	void returnsEmptyWhenSeparatorMissing() {
		assertThat(RiotIdParser.parse("Peyz KR11")).isEmpty();
	}

	@Test
	void stripsTrailingTierTextBeforeParsing() {
		RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse("Vanana#0110 Diamond IV")
				.orElseThrow();

		assertThat(parsed.gameName()).isEqualTo("Vanana");
		assertThat(parsed.tagLine()).isEqualTo("0110");
		assertThat(parsed.normalizedRiotId()).isEqualTo("Vanana#0110");
	}

	@Test
	void stripsTrackingSiteUnrankedDecorationsBeforeParsing() {
		RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse("하이뿌#KR0 Unranked Show Inactive (1)")
				.orElseThrow();

		assertThat(parsed.gameName()).isEqualTo("하이뿌");
		assertThat(parsed.tagLine()).isEqualTo("KR0");
		assertThat(parsed.normalizedRiotId()).isEqualTo("하이뿌#KR0");
	}

	@Test
	void stripsPlainUnrankedSuffixBeforeParsing() {
		RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse("HamBak#kr0 Unranked")
				.orElseThrow();

		assertThat(parsed.gameName()).isEqualTo("HamBak");
		assertThat(parsed.tagLine()).isEqualTo("kr0");
		assertThat(parsed.normalizedRiotId()).isEqualTo("HamBak#kr0");
	}
}
