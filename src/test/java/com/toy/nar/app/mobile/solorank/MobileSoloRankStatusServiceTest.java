package com.toy.nar.app.mobile.solorank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.app.mobile.solorank.dto.SoloRankStatusResponse;
import com.toy.nar.app.mobile.subscription.MobilePlayerSubscriptionService;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionResponse;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;

class MobileSoloRankStatusServiceTest {

	private MobilePlayerSubscriptionService subscriptionService;
	private PlayerSoloRankGameRepository gameRepository;
	private MobileSoloRankStatusService service;

	@BeforeEach
	void setUp() {
		subscriptionService = mock(MobilePlayerSubscriptionService.class);
		gameRepository = mock(PlayerSoloRankGameRepository.class);
		service = new MobileSoloRankStatusService(subscriptionService, gameRepository);
	}

	private static PlayerSubscriptionResponse sub(long id, String name, String team) {
		return new PlayerSubscriptionResponse(id, name, name + ".png", "MID", 1L, team, team, team + ".png",
				true, true, false);
	}

	private static PlayerSoloRankGame game(long playerId, String gameId, LocalDateTime detectedAt,
			LocalDateTime startedAt, LocalDateTime endedAt) {
		Player player = Player.builder().name("p" + playerId).build();
		ReflectionTestUtils.setField(player, "id", playerId);
		PlayerSoloRankGame game = new PlayerSoloRankGame(player, gameId, null, detectedAt);
		ReflectionTestUtils.setField(game, "gameStartedAt", startedAt);
		ReflectionTestUtils.setField(game, "gameEndedAt", endedAt);
		ReflectionTestUtils.setField(game, "win", endedAt == null ? null : true);
		return game;
	}

	@Test
	void 구독이_없으면_조회하지_않는다() {
		when(subscriptionService.getSubscriptions(7L)).thenReturn(List.of());

		SoloRankStatusResponse response = service.getStatus(7L);

		assertThat(response.live()).isEmpty();
		assertThat(response.finished()).isEmpty();
		verify(gameRepository, never()).findLive(anyCollection(), any(), any());
	}

	@Test
	void 라이브는_최근_시작순이고_시작_시각이_없으면_감지_시각을_쓴다() {
		LocalDateTime now = LocalDateTime.now();
		when(subscriptionService.getSubscriptions(7L))
				.thenReturn(List.of(sub(1, "Faker", "T1"), sub(2, "Chovy", "GEN")));
		when(gameRepository.findLive(anyCollection(), any(), any())).thenReturn(List.of(
				game(1, "a", now.minusMinutes(30), now.minusMinutes(28), null),
				game(2, "b", now.minusMinutes(5), null, null))); // 로딩 화면에 감지 — 시작 모름
		when(gameRepository.findFinishedSince(anyCollection(), any(), any())).thenReturn(List.of());

		SoloRankStatusResponse response = service.getStatus(7L);

		assertThat(response.live()).extracting(SoloRankStatusResponse.LivePlayer::playerName)
				.containsExactly("Chovy", "Faker");
		assertThat(response.live().get(0).startedAt().toLocalDateTime()).isEqualTo(now.minusMinutes(5));
		assertThat(response.live().get(1).teamCode()).isEqualTo("T1");
	}

	@Test
	void 끝난_게임은_선수당_마지막_한_판만() {
		LocalDateTime now = LocalDateTime.now();
		when(subscriptionService.getSubscriptions(7L))
				.thenReturn(List.of(sub(1, "Faker", "T1"), sub(2, "Chovy", "GEN")));
		when(gameRepository.findLive(anyCollection(), any(), any())).thenReturn(List.of());
		// 레포는 최근 종료순으로 준다
		when(gameRepository.findFinishedSince(anyCollection(), any(), any())).thenReturn(List.of(
				game(1, "f3", now.minusHours(1), null, now.minusMinutes(10)),
				game(2, "c1", now.minusHours(2), null, now.minusMinutes(40)),
				game(1, "f2", now.minusHours(3), null, now.minusMinutes(90))));

		SoloRankStatusResponse response = service.getStatus(7L);

		assertThat(response.finished()).extracting(SoloRankStatusResponse.FinishedPlayer::playerName)
				.containsExactly("Faker", "Chovy");
		assertThat(response.finished().get(0).endedAt().toLocalDateTime()).isEqualTo(now.minusMinutes(10));
	}
}
