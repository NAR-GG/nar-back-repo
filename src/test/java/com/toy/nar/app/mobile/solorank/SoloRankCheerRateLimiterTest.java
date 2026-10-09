package com.toy.nar.app.mobile.solorank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class SoloRankCheerRateLimiterTest {

	private final AtomicLong nanos = new AtomicLong();
	private final SoloRankCheerRateLimiter limiter = new SoloRankCheerRateLimiter(nanos::get);

	@Test
	void 버스트_60까지_받고_넘으면_거절한다() {
		assertThat(limiter.tryConsume(1L, 30)).isTrue();
		assertThat(limiter.tryConsume(1L, 30)).isTrue();
		assertThat(limiter.tryConsume(1L, 1)).isFalse();
	}

	@Test
	void 초당_20회씩_채워진다() {
		limiter.tryConsume(1L, 60);
		assertThat(limiter.tryConsume(1L, 1)).isFalse();

		nanos.addAndGet(1_000_000_000L); // 1초 → 20개
		assertThat(limiter.tryConsume(1L, 20)).isTrue();
		assertThat(limiter.tryConsume(1L, 1)).isFalse();
	}

	@Test
	void 거절된_요청은_토큰을_쓰지_않고_회원끼리_섞이지_않는다() {
		limiter.tryConsume(1L, 50);
		assertThat(limiter.tryConsume(1L, 30)).isFalse();
		assertThat(limiter.tryConsume(1L, 10)).isTrue(); // 거절이 10개를 깎지 않았다
		assertThat(limiter.tryConsume(2L, 60)).isTrue();
	}
}
