package com.toy.nar.app.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일정 조회의 "하루" 경계. match_date 는 UTC 로 저장되고 화면은 KST 로 그리므로,
 * 조회 경계도 KST 하루를 UTC 로 옮긴 값이어야 한다.
 *
 * <p>예전에는 KST 날짜를 그대로 넣어 실제 구간이 KST 09:00 ~ 다음날 08:59 였다.
 * LCK(17·19시)는 우연히 맞아 안 드러났고, KST 새벽에 열리는 LEC·LCS 만 하루 앞
 * 페이지에 붙어 "어제 끝난 경기가 오늘 unstarted 로 보인다"로 나타났다.
 */
class ScheduleFinderDayBoundaryTest {

	private static final LocalDate DAY = LocalDate.of(2026, 8, 23);

	private static LocalDateTime startUtc() {
		return ScheduleFinder.toUtc(DAY.atStartOfDay(ScheduleFinder.KST));
	}

	private static LocalDateTime endUtc() {
		return ScheduleFinder.toUtc(DAY.plusDays(1).atStartOfDay(ScheduleFinder.KST)).minusSeconds(1);
	}

	private static boolean inRange(LocalDateTime matchDateUtc) {
		return !matchDateUtc.isBefore(startUtc()) && !matchDateUtc.isAfter(endUtc());
	}

	@Test
	void KST_하루가_UTC_로_9시간_당겨진다() {
		assertThat(startUtc()).isEqualTo(LocalDateTime.of(2026, 8, 22, 15, 0, 0));
		assertThat(endUtc()).isEqualTo(LocalDateTime.of(2026, 8, 23, 14, 59, 59));
	}

	@Test
	void KST_새벽_경기가_그날에_포함된다() {
		// LEC 08-23 00:00 KST = 08-22 15:00 UTC — 예전에는 08-22 페이지에 붙었다
		assertThat(inRange(LocalDateTime.of(2026, 8, 22, 15, 0))).isTrue();
		// LCS 08-23 08:00 KST = 08-22 23:00 UTC
		assertThat(inRange(LocalDateTime.of(2026, 8, 22, 23, 0))).isTrue();
	}

	@Test
	void KST_저녁_경기는_그대로_그날이다() {
		// LCK 08-23 17:00 KST = 08-23 08:00 UTC — 원래도 맞던 구간이라 회귀가 없어야 한다
		assertThat(inRange(LocalDateTime.of(2026, 8, 23, 8, 0))).isTrue();
		// LPL 08-23 20:00 KST = 08-23 11:00 UTC
		assertThat(inRange(LocalDateTime.of(2026, 8, 23, 11, 0))).isTrue();
	}

	@Test
	void 다음날_새벽_경기는_빠진다() {
		// LEC 08-24 00:00 KST = 08-23 15:00 UTC — 이게 08-23 에 섞여 들어와 오해를 만들었다
		assertThat(inRange(LocalDateTime.of(2026, 8, 23, 15, 0))).isFalse();
	}

	@Test
	void 전날_밤_경기는_빠진다() {
		// 08-22 23:59 KST = 08-22 14:59 UTC
		assertThat(inRange(LocalDateTime.of(2026, 8, 22, 14, 59))).isFalse();
	}
}
