package com.toy.nar.app.lolesports;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회 구간이 KST 기준으로 잘리는지. match_date 는 오프셋 없는 UTC 벽시계라
 * KST 날짜를 그대로 넣으면 담기는 구간이 KST 09:00 ~ 다음날 08:59 가 된다.
 *
 * <p>LCK(17·19시)는 그 어긋남 안에 우연히 들어와 안 드러났고, KST 새벽에 열리는
 * LEC·LCS·CBLOL 만 하루 앞 날짜에 붙었다. 아래 케이스가 그 경계를 잠근다.
 */
class MatchDateWindowTest {

	private static final LocalDate DAY = LocalDate.of(2026, 8, 23);

	private static boolean inDay(LocalDate kstDay, LocalDateTime matchDate) {
		return !matchDate.isBefore(MatchDateWindow.startOfDay(kstDay))
				&& !matchDate.isAfter(MatchDateWindow.endOfDay(kstDay));
	}

	@Test
	void KST_하루가_UTC_로_9시간_당겨진다() {
		assertThat(MatchDateWindow.startOfDay(DAY)).isEqualTo(LocalDateTime.of(2026, 8, 22, 15, 0, 0));
		assertThat(MatchDateWindow.endOfDay(DAY)).isEqualTo(LocalDateTime.of(2026, 8, 23, 14, 59, 59));
	}

	@Test
	void KST_새벽_경기가_그날에_포함된다() {
		// LEC 08-23 00:00 KST = 08-22 15:00 UTC — 예전에는 08-22 페이지에 붙었다
		assertThat(inDay(DAY, LocalDateTime.of(2026, 8, 22, 15, 0))).isTrue();
		// LCS 08-23 08:00 KST = 08-22 23:00 UTC
		assertThat(inDay(DAY, LocalDateTime.of(2026, 8, 22, 23, 0))).isTrue();
	}

	@Test
	void KST_저녁_경기는_그대로_그날이다() {
		// LCK 17:00 KST = 08:00 UTC, LPL 20:00 KST = 11:00 UTC — 원래 맞던 구간이라 회귀가 없어야 한다
		assertThat(inDay(DAY, LocalDateTime.of(2026, 8, 23, 8, 0))).isTrue();
		assertThat(inDay(DAY, LocalDateTime.of(2026, 8, 23, 11, 0))).isTrue();
	}

	@Test
	void 이웃한_날의_경기는_빠진다() {
		// 08-24 00:00 KST = 08-23 15:00 UTC — 이게 08-23 에 섞여 들어와 오해를 만들었다
		assertThat(inDay(DAY, LocalDateTime.of(2026, 8, 23, 15, 0))).isFalse();
		// 08-22 23:59 KST = 08-22 14:59 UTC
		assertThat(inDay(DAY, LocalDateTime.of(2026, 8, 22, 14, 59))).isFalse();
	}

	@Test
	void 달_경계도_KST_로_잘린다() {
		YearMonth august = YearMonth.of(2026, 8);
		LocalDateTime start = MatchDateWindow.startOfDay(august.atDay(1));
		LocalDateTime end = MatchDateWindow.endOfDay(august.atEndOfMonth());

		// 8/1 00:00 KST = 7/31 15:00 UTC, 8/31 23:59:59 KST = 8/31 14:59:59 UTC
		assertThat(start).isEqualTo(LocalDateTime.of(2026, 7, 31, 15, 0, 0));
		assertThat(end).isEqualTo(LocalDateTime.of(2026, 8, 31, 14, 59, 59));
		// 9/1 00:00 KST 경기(= 8/31 15:00 UTC)가 8월 달력에 새면 안 된다
		assertThat(LocalDateTime.of(2026, 8, 31, 15, 0)).isAfter(end);
	}

	@Test
	void 해_경계도_KST_로_잘린다() {
		LocalDateTime start = MatchDateWindow.startOfDay(LocalDate.of(2026, 1, 1));
		LocalDateTime end = MatchDateWindow.endOfDay(LocalDate.of(2026, 12, 31));

		assertThat(start).isEqualTo(LocalDateTime.of(2025, 12, 31, 15, 0, 0));
		assertThat(end).isEqualTo(LocalDateTime.of(2026, 12, 31, 14, 59, 59));
	}
}
