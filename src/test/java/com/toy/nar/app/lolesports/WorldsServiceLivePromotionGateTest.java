package com.toy.nar.app.lolesports;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * games[] 의 inProgress 를 매치 상태로 승격할지 가르는 게이트.
 *
 * <p>업스트림이 시작도 안 한 경기의 1세트를 inProgress 로 흘린 실사고(2026-08-22 LPL TT vs LGD,
 * 예정 22시간 전)를 막는 장치다. 반대로 진행 중인 경기를 놓치면 라이브위젯·시작 알림이 통째로
 * 빠지므로, 애매하면 막지 않는 쪽이 정답이다 — 마지막 두 케이스가 그 규약을 잠근다.
 */
class WorldsServiceLivePromotionGateTest {

	private static String iso(Duration fromNow) {
		return Instant.now().plus(fromNow).toString();
	}

	@Test
	void 예정_시각이_한참_남았으면_승격을_막는다() {
		assertThat(WorldsService.notYetStarted(iso(Duration.ofHours(22)))).isTrue();
	}

	@Test
	void 시작_5분_전_창_안이면_승격을_허용한다() {
		// 중계는 예정 시각보다 조금 일찍 열린다. 창 안이면 업스트림을 믿는다.
		assertThat(WorldsService.notYetStarted(iso(Duration.ofMinutes(1)))).isFalse();
	}

	@Test
	void 이미_시작한_경기는_승격을_허용한다() {
		assertThat(WorldsService.notYetStarted(iso(Duration.ofHours(-1)))).isFalse();
	}

	@Test
	void 시각을_못_읽으면_막지_않는다() {
		// 놓치는 손해가 오표시보다 크다. 확실히 미래일 때만 막는다.
		assertThat(WorldsService.notYetStarted(null)).isFalse();
		assertThat(WorldsService.notYetStarted("")).isFalse();
		assertThat(WorldsService.notYetStarted("2026-08-23 07:00:00")).isFalse();
	}
}
