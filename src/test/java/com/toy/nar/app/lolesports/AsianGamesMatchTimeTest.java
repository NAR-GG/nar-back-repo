package com.toy.nar.app.lolesports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * lolesports 가 아시안게임 경기 시각을 태평양 시간 벽시계에 {@code Z} 를 붙여 내보낸다.
 * 공식 시각(일본e스포츠협회·경기장 세션표)과 맞추는 보정을 잠근다.
 */
class AsianGamesMatchTimeTest {

	/** 저장값은 오프셋 없는 UTC 벽시계다. 화면에 뜨는 KST 로 환산해 검증한다. */
	private static LocalDateTime kst(String leagueName, String apiValue) {
		LocalDateTime parsed = LocalDateTime.parse(apiValue.replace("Z", ""));
		return LeagueConstants.correctMatchDate(leagueName, parsed).plusHours(9);
	}

	@Test
	@DisplayName("공식 일정과 같은 시각이 된다 — 그룹 09:00·11:30·14:00, 4강 09:00·13:30, 결승 12:00")
	void matchesOfficialSchedule() {
		// 그룹 스테이지 A조 (현지 9/29)
		assertThat(kst("ASIAN_GAMES", "2026-09-28T17:00:00Z")).isEqualTo(LocalDateTime.parse("2026-09-29T09:00"));
		assertThat(kst("ASIAN_GAMES", "2026-09-28T19:30:00Z")).isEqualTo(LocalDateTime.parse("2026-09-29T11:30"));
		assertThat(kst("ASIAN_GAMES", "2026-09-28T22:00:00Z")).isEqualTo(LocalDateTime.parse("2026-09-29T14:00"));
		// 4강
		assertThat(kst("ASIAN_GAMES", "2026-09-30T17:00:00Z")).isEqualTo(LocalDateTime.parse("2026-10-01T09:00"));
		assertThat(kst("ASIAN_GAMES", "2026-09-30T21:30:00Z")).isEqualTo(LocalDateTime.parse("2026-10-01T13:30"));
		// 결승 — 일본e스포츠협회 공식 일정표가 12:00 으로 못 박은 값
		assertThat(kst("ASIAN_GAMES", "2026-10-01T20:00:00Z")).isEqualTo(LocalDateTime.parse("2026-10-02T12:00"));
	}

	@Test
	@DisplayName("두 번 적용해도 같다 — 업스트림이 고치면 보정이 저절로 멈춘다")
	void isIdempotent() {
		LocalDateTime raw = LocalDateTime.parse("2026-09-28T17:00");
		LocalDateTime once = LeagueConstants.correctMatchDate("ASIAN_GAMES", raw);
		LocalDateTime twice = LeagueConstants.correctMatchDate("ASIAN_GAMES", once);
		assertThat(twice).isEqualTo(once);
		// 업스트림이 제대로 된 UTC 를 주기 시작하면(KST 09:00) 손대지 않는다.
		LocalDateTime fixed = LocalDateTime.parse("2026-09-29T00:00");
		assertThat(LeagueConstants.correctMatchDate("ASIAN_GAMES", fixed)).isEqualTo(fixed);
	}

	@Test
	@DisplayName("다른 리그는 건드리지 않는다")
	void leavesOtherLeaguesAlone() {
		LocalDateTime lckDawn = LocalDateTime.parse("2026-09-28T17:00");
		assertThat(LeagueConstants.correctMatchDate("LCK", lckDawn)).isEqualTo(lckDawn);
		assertThat(LeagueConstants.correctMatchDate("WORLDS", lckDawn)).isEqualTo(lckDawn);
		assertThat(LeagueConstants.correctMatchDate(null, lckDawn)).isEqualTo(lckDawn);
		assertThat(LeagueConstants.correctMatchDate("ASIAN_GAMES", null)).isNull();
	}
}
