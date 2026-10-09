package com.toy.nar.app.mobile.solorank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.app.mobile.solorank.dto.SoloRankCheerResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.GameKey;
import com.toy.nar.domain.participant.repository.PlayerSoloRankGameRepository;

class SoloRankCheerServiceTest {

	private PlayerSoloRankGameRepository gameRepository;
	private PlayerSoloRankCheerRepository cheerRepository;
	private SoloRankCheerService service;

	@BeforeEach
	void setUp() {
		gameRepository = mock(PlayerSoloRankGameRepository.class);
		cheerRepository = mock(PlayerSoloRankCheerRepository.class);
		service = new SoloRankCheerService(gameRepository, cheerRepository);
	}

	private static PlayerSoloRankGame liveGame(long playerId, String gameId) {
		Player player = Player.builder().name("p" + playerId).build();
		ReflectionTestUtils.setField(player, "id", playerId);
		return new PlayerSoloRankGame(player, gameId, null, LocalDateTime.now());
	}

	@Test
	void 솔랭_중이면_판에_누적하고_합계를_돌려준다() {
		when(gameRepository.findLive(any(), any(), any())).thenReturn(List.of(liveGame(5L, "KR_1")));
		GameKey key = new GameKey(5L, "KR_1");
		when(cheerRepository.totals(any(), any())).thenReturn(Map.of(key, 120L));
		when(cheerRepository.mine(anyLong(), any(), any())).thenReturn(Map.of(key, 7L));

		SoloRankCheerResponse response = service.cheer(9L, 5L, 3);

		verify(cheerRepository).add(5L, "KR_1", 9L, 3);
		assertThat(response.cheerTotal()).isEqualTo(120L);
		assertThat(response.cheerMine()).isEqualTo(7L);
	}

	@Test
	void 솔랭_중이_아니면_409로_거절한다() {
		when(gameRepository.findLive(any(), any(), any())).thenReturn(List.of());

		assertThatThrownBy(() -> service.cheer(9L, 5L, 1))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOLO_RANK_NOT_LIVE));
		verify(cheerRepository, never()).add(anyLong(), anyString(), anyLong(), anyInt());
	}

	@Test
	void 횟수가_범위_밖이면_400으로_거절한다() {
		for (int count : new int[] {0, -1, SoloRankCheerService.MAX_COUNT_PER_REQUEST + 1}) {
			assertThatThrownBy(() -> service.cheer(9L, 5L, count))
					.isInstanceOfSatisfying(CustomException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
		}
		verify(cheerRepository, never()).add(anyLong(), anyString(), anyLong(), anyInt());
	}
}
