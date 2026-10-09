package com.toy.nar.app.mobile.solorank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository;
import com.toy.nar.domain.participant.repository.PlayerSoloRankCheerRepository.GameKey;

class SoloRankCheerTotalsTest {

	private final PlayerSoloRankCheerRepository repository = mock(PlayerSoloRankCheerRepository.class);
	private final SoloRankCheerTotals totals = new SoloRankCheerTotals(repository);

	@Test
	void 같은_판은_캐시에서_주고_없는_판만_조회한다() {
		GameKey a = new GameKey(1L, "a");
		GameKey b = new GameKey(2L, "b");
		when(repository.totals(any(), any())).thenReturn(Map.of(a, 10L));

		assertThat(totals.live(List.of(a)).get(a)).isEqualTo(10L);
		assertThat(totals.live(List.of(a)).get(a)).isEqualTo(10L);
		verify(repository, times(1)).totals(any(), any());

		// b 는 응원이 없어 레포가 키를 안 준다 → 0 으로 캐시한다
		assertThat(totals.live(List.of(a, b)).get(b)).isEqualTo(0L);
		assertThat(totals.live(List.of(b)).get(b)).isEqualTo(0L);
		verify(repository, times(2)).totals(any(), any());
	}
}
