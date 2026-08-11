package com.toy.nar.app.mobile.push;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuietAwarePushSenderTest {

	@Mock
	private MobilePushGateway pushGateway;

	@Mock
	private QuietHoursResolver quietHoursResolver;

	private final MobilePushMessage message =
			new MobilePushMessage("제목", "본문", Map.of("type", "TEST"));

	private QuietAwarePushSender sender() {
		return new QuietAwarePushSender(pushGateway, quietHoursResolver);
	}

	private static Map<Long, List<String>> tokens() {
		Map<Long, List<String>> byMember = new LinkedHashMap<>();
		byMember.put(1L, List.of("loud-a", "loud-b"));
		byMember.put(2L, List.of("quiet-a"));
		return byMember;
	}

	@Test
	void 잠자기_회원은_발송에서_빠지고_건너뛴_회원으로_돌아온다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of(2L));
		when(pushGateway.send(eq(List.of("loud-a", "loud-b")), any()))
				.thenReturn(new MobilePushResult(2, 0, List.of(), List.of("loud-a", "loud-b")));

		QuietAwarePushSender.Outcome outcome = sender().send(tokens(), message);

		// 잠자기 회원 토큰은 발송 목록에 아예 들어가지 않는다.
		verify(pushGateway, times(1)).send(List.of("loud-a", "loud-b"), message);
		assertThat(outcome.skippedMemberIds()).containsExactly(2L);
		assertThat(outcome.isSkipped(2L)).isTrue();
		assertThat(outcome.isSkipped(1L)).isFalse();
		assertThat(outcome.result().successTokens()).containsExactly("loud-a", "loud-b");
	}

	@Test
	void 잠자기_회원이_없으면_전원에게_한_번_보낸다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of());
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(3, 0, List.of(), List.of("loud-a", "loud-b", "quiet-a")));

		QuietAwarePushSender.Outcome outcome = sender().send(tokens(), message);

		verify(pushGateway, times(1)).send(List.of("loud-a", "loud-b", "quiet-a"), message);
		assertThat(outcome.skippedMemberIds()).isEmpty();
	}

	@Test
	void 전원_잠자기면_게이트웨이를_아예_부르지_않는다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of(1L, 2L));

		QuietAwarePushSender.Outcome outcome = sender().send(tokens(), message);

		// 빈 토큰 리스트로 FCM 을 부르면 무의미한 왕복이 생긴다.
		verify(pushGateway, never()).send(any(), any());
		assertThat(outcome.skippedMemberIds()).containsExactly(1L, 2L);
		assertThat(outcome.result().successTokens()).isEmpty();
		assertThat(outcome.result().successCount()).isZero();
	}

	@Test
	void 토큰_순서를_보존한다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of());
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(3, 0, List.of(), List.of()));

		sender().send(tokens(), message);

		// 호출처가 LinkedHashMap 을 넘기고 팬아웃 테스트가 토큰 순서로 단정한다.
		verify(pushGateway).send(List.of("loud-a", "loud-b", "quiet-a"), message);
	}
}
