package com.toy.nar.app.mobile.push;

import com.toy.nar.app.monitor.SchedulerAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushDeliveryRetentionServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 4, 50);

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	/** 35일, 청크 5000, 청크 상한 3. */
	private final PushDeliveryRetentionService service =
			new PushDeliveryRetentionService(jdbcTemplate, mock(SchedulerAlertService.class), 35, 5000, 3);

	@Test
	void 두_테이블의_모든_상태를_35일_컷오프로_지운다() {
		when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(0);

		service.purge(NOW);

		for (String table : new String[] {"player_solo_rank_push_delivery", "member_team_event_push_delivery"}) {
			for (String status : new String[] {"PENDING", "SENT", "FAILED", "SKIPPED_QUIET"}) {
				verify(jdbcTemplate).update(contains("DELETE FROM " + table + " "),
						eq(status), eq(NOW.minusDays(35)), eq(5000));
			}
		}
	}

	@Test
	void 가득_찬_청크는_이어_지우고_상한에서_멈춘다() {
		when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(0);
		// 솔랭 SENT 는 계속 가득 차고(상한 3회에서 멈춤), 팀 SENT 는 5000 + 1200 으로 끝난다.
		when(jdbcTemplate.update(contains("player_solo_rank_push_delivery"), eq("SENT"), any(), any()))
				.thenReturn(5000);
		when(jdbcTemplate.update(contains("member_team_event_push_delivery"), eq("SENT"), any(), any()))
				.thenReturn(5000, 1200);

		int deleted = service.purge(NOW);

		assertThat(deleted).isEqualTo(15000 + 6200);
		verify(jdbcTemplate, times(3)).update(contains("player_solo_rank_push_delivery"), eq("SENT"), any(), any());
		verify(jdbcTemplate, times(2)).update(contains("member_team_event_push_delivery"), eq("SENT"), any(), any());
	}
}
