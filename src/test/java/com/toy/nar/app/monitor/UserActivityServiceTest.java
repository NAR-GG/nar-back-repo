package com.toy.nar.app.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지표가 조용히 거짓말하는 경우를 잠근다 — 값이 틀려도 대시보드는 초록으로 보인다.
 */
class UserActivityServiceTest {

	/** 테스트가 시간을 밀 수 있는 Clock. Asia/Seoul 로 고정해 자정 판정을 재현 가능하게 한다. */
	static class MovableClock extends Clock {
		private Instant now;
		private final ZoneId zone;

		MovableClock(Instant now, ZoneId zone) {
			this.now = now;
			this.zone = zone;
		}

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId z) {
			return new MovableClock(now, z) {
				@Override
				public Instant instant() {
					return MovableClock.this.instant();
				}
			};
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Test
	@DisplayName("5분이 지나면 활성 사용자에서 빠지지만, 금일 사용자에는 남는다")
	void activeWindowExpiresButDailyPersists() {
		// 2026-08-23 10:00 KST
		MovableClock clock = new MovableClock(Instant.parse("2026-08-23T01:00:00Z"), KST);
		UserActivityService service = new UserActivityService(clock);

		service.recordUserActivity("m:1");
		service.recordUserActivity("i:1.2.3.4");
		assertThat(service.getActiveUsersCount()).isEqualTo(2);
		assertThat(service.getDailyUsersCount()).isEqualTo(2);

		clock.advance(Duration.ofMinutes(6));

		assertThat(service.getActiveUsersCount()).isZero();
		assertThat(service.getDailyUsersCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("같은 사용자가 여러 번 요청해도 한 명이다")
	void sameIdentifierCountsOnce() {
		MovableClock clock = new MovableClock(Instant.parse("2026-08-23T01:00:00Z"), KST);
		UserActivityService service = new UserActivityService(clock);

		service.recordUserActivity("m:1");
		service.recordUserActivity("m:1");
		service.recordUserActivity("m:1");

		assertThat(service.getActiveUsersCount()).isEqualTo(1);
		assertThat(service.getDailyUsersCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("KST 자정을 넘기면 금일 사용자가 리셋된다 — UTC 자정이 아니다")
	void dailyResetsAtKstMidnight() {
		// 2026-08-23 23:50 KST = 14:50 UTC
		MovableClock clock = new MovableClock(Instant.parse("2026-08-23T14:50:00Z"), KST);
		UserActivityService service = new UserActivityService(clock);

		service.recordUserActivity("m:1");
		assertThat(service.getDailyUsersCount()).isEqualTo(1);

		// UTC 자정(09:00 KST)을 지나도 리셋되면 안 된다
		clock.advance(Duration.ofMinutes(5));
		assertThat(service.getDailyUsersCount()).isEqualTo(1);

		// KST 자정 통과
		clock.advance(Duration.ofMinutes(10));
		assertThat(service.getDailyUsersCount()).isZero();

		service.recordUserActivity("m:2");
		assertThat(service.getDailyUsersCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("정리는 만료된 항목만 지운다")
	void cleanupRemovesOnlyExpired() {
		MovableClock clock = new MovableClock(Instant.parse("2026-08-23T01:00:00Z"), KST);
		UserActivityService service = new UserActivityService(clock);

		service.recordUserActivity("m:old");
		clock.advance(Duration.ofMinutes(6));
		service.recordUserActivity("m:new");

		service.cleanupOldUsers();

		assertThat(service.getActiveUsersCount()).isEqualTo(1);
		assertThat(service.getDailyUsersCount()).isEqualTo(2);
	}
}
