package com.toy.nar.app.player;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PlayerProfileCrawlerServiceTest {

	private static List<PlayerProfileDto.GameAccountDto> parse(String accountsText) {
		return PlayerProfileCrawlerService.extractGameAccounts(
				Jsoup.parse("<div><h4>Accounts</h4><p>" + accountsText + "</p></div>"));
	}

	@Test
	void 아이디_안의_티어_글자를_티어로_자르지_않는다() {
		// prod 에 실제로 깨져 저장된 계정들(player 212, 99, 258, 383)
		assertThat(parse("[KR] songman#KR11 GM 1,860LP [KR] FA Rich#KR1 Ch 868LP [KR] Rich#8686 Diamond I"
				+ " [KR] 14March#KR1 Unranked [EUW] Pom Michutda#EUW Unranked"))
				.extracting("region", "riotId", "tier")
				.containsExactly(
						tuple("KR", "songman#KR11", "GM 1,860LP"),
						tuple("KR", "FA Rich#KR1", "Ch 868LP"),
						tuple("KR", "Rich#8686", "Diamond I"),
						tuple("KR", "14March#KR1 Unranked", null),
						tuple("EUW", "Pom Michutda#EUW Unranked", null));
	}

	@Test
	void 정상_계정_티어는_그대로_자른다() {
		assertThat(parse("[KR] Hide on bush#KR1 Challenger 1,200LP [KR] Peyz#KR11 Master 67LP"
				+ " [KR] 뜨거운감자#asd Diamond II Show Inactive (1) [NA] corejjoppa#NA1 Unranked [KR] 뜨거운감자#asd"))
				.extracting("riotId", "tier")
				.containsExactly(
						tuple("Hide on bush#KR1", "Challenger 1,200LP"),
						tuple("Peyz#KR11", "Master 67LP"),
						tuple("뜨거운감자#asd", "Diamond II Show Inactive (1)"),
						tuple("corejjoppa#NA1 Unranked", null),
						tuple("뜨거운감자#asd", null));
	}
}
