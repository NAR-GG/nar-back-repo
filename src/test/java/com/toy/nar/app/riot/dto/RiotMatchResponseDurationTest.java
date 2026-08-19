package com.toy.nar.app.riot.dto;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 경기 길이 계산. {@code info.gameDuration} 대신 타임스탬프 차이를 쓰는 이유는
 * 그 필드가 11.20 패치 이전/이후로 단위(ms/초)가 갈리기 때문이다.
 */
class RiotMatchResponseDurationTest {

	@Test
	@DisplayName("두 타임스탬프 차이를 초로 돌려준다")
	void 차이를_초로_돌려준다() {
		assertThat(info(1_700_000_000_000L, 1_700_000_000_000L + 1_694_000L).durationSeconds())
				.isEqualTo(1694);
	}

	@Test
	@DisplayName("초 미만은 버린다")
	void 초_미만은_버린다() {
		assertThat(info(1_000L, 1_000L + 1_999L).durationSeconds()).isEqualTo(1);
	}

	/** 진행 중인 매치는 gameEndTimestamp 가 없다. 그때 0분으로 표시하면 안 된다. */
	@Test
	@DisplayName("타임스탬프가 없으면 null")
	void 값이_없으면_null() {
		assertThat(info(null, 1_000L).durationSeconds()).isNull();
		assertThat(info(1_000L, null).durationSeconds()).isNull();
		assertThat(info(null, null).durationSeconds()).isNull();
	}

	/** 시계 역전·데이터 이상. 음수 분을 알림에 실으면 안 된다. */
	@Test
	@DisplayName("끝이 시작보다 빠르거나 같으면 null")
	void 역전이면_null() {
		assertThat(info(2_000L, 1_000L).durationSeconds()).isNull();
		assertThat(info(1_000L, 1_000L).durationSeconds()).isNull();
	}

	private RiotMatchResponse.Info info(Long start, Long end) {
		return new RiotMatchResponse.Info(420, start, end, List.of());
	}
}
