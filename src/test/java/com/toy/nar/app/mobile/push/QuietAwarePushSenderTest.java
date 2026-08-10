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
	void 잠자기_회원과_아닌_회원을_따로_보낸다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of(2L));
		when(pushGateway.send(eq(List.of("loud-a", "loud-b")), any()))
				.thenReturn(new MobilePushResult(2, 0, List.of(), List.of("loud-a", "loud-b")));
		when(pushGateway.send(eq(List.of("quiet-a")), any()))
				.thenReturn(new MobilePushResult(1, 0, List.of(), List.of("quiet-a")));

		MobilePushResult result = sender().send(tokens(), message);

		verify(pushGateway).send(List.of("loud-a", "loud-b"), message);
		verify(pushGateway).send(List.of("quiet-a"), message.asSilent());
		assertThat(result.successCount()).isEqualTo(3);
		assertThat(result.successTokens()).containsExactlyInAnyOrder("loud-a", "loud-b", "quiet-a");
	}

	@Test
	void 잠자기_회원이_없으면_한_번만_보낸다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of());
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(3, 0, List.of(), List.of("loud-a", "loud-b", "quiet-a")));

		sender().send(tokens(), message);

		verify(pushGateway, times(1)).send(any(), any());
		verify(pushGateway).send(List.of("loud-a", "loud-b", "quiet-a"), message);
	}

	@Test
	void 전원_잠자기면_무음으로_한_번만_보낸다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of(1L, 2L));
		when(pushGateway.send(any(), any()))
				.thenReturn(new MobilePushResult(3, 0, List.of(), List.of()));

		sender().send(tokens(), message);

		verify(pushGateway, times(1)).send(any(), any());
		verify(pushGateway).send(List.of("loud-a", "loud-b", "quiet-a"), message.asSilent());
	}

	@Test
	void 실패건수와_무효토큰도_합친다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of(2L));
		when(pushGateway.send(eq(List.of("loud-a", "loud-b")), any()))
				.thenReturn(new MobilePushResult(1, 1, List.of("loud-b"), List.of("loud-a")));
		when(pushGateway.send(eq(List.of("quiet-a")), any()))
				.thenReturn(new MobilePushResult(0, 1, List.of("quiet-a"), List.of()));

		MobilePushResult result = sender().send(tokens(), message);

		assertThat(result.failureCount()).isEqualTo(2);
		assertThat(result.invalidTokens()).containsExactlyInAnyOrder("loud-b", "quiet-a");
	}

	@Test
	void 무음_발송이_실패해도_소리_그룹의_배달_기록은_지우지_않는다() {
		when(quietHoursResolver.quietMemberIds(any())).thenReturn(Set.of(2L));
		when(pushGateway.send(eq(List.of("loud-a", "loud-b")), any()))
				.thenReturn(new MobilePushResult(2, 0, List.of(), List.of("loud-a", "loud-b")));
		when(pushGateway.send(eq(List.of("quiet-a")), any()))
				.thenThrow(new IllegalStateException("FCM down"));

		MobilePushResult result = sender().send(tokens(), message);

		// 소리 그룹은 성공으로 남아 호출처가 SENT + 피드 기록을 할 수 있어야 한다.
		assertThat(result.successCount()).isEqualTo(2);
		assertThat(result.successTokens()).containsExactly("loud-a", "loud-b");
		// 무음 그룹만 실패로 집계돼 재예약 대상이 된다.
		assertThat(result.failureCount()).isEqualTo(1);
	}
}
