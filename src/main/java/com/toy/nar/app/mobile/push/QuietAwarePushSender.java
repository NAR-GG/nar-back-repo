package com.toy.nar.app.mobile.push;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 알림 잠자기를 반영해 멀티캐스트한다.
 *
 * <p>구독자를 잠자기 걸린 집합 / 안 걸린 집합 2그룹으로 나눠 최대 2회 보낸다.
 * 회원별로 쪼개지 않는다 — 2026-08-04 프로덕션에서 구독자 1,502명 개별 발송이 472초 걸려
 * 솔랭 폴 스레드를 통째로 막은 적이 있다. 2그룹이면 FCM 왕복이 1→2회로만 늘고 O(1)이 유지된다.</p>
 */
@Component
@RequiredArgsConstructor
public class QuietAwarePushSender {

	private final MobilePushGateway pushGateway;
	private final QuietHoursResolver quietHoursResolver;

	public MobilePushResult send(Map<Long, List<String>> tokensByMember, MobilePushMessage message) {
		Set<Long> quietMemberIds = quietHoursResolver.quietMemberIds(tokensByMember.keySet());

		List<String> loudTokens = new ArrayList<>();
		List<String> quietTokens = new ArrayList<>();
		tokensByMember.forEach((memberId, tokens) ->
				(quietMemberIds.contains(memberId) ? quietTokens : loudTokens).addAll(tokens));

		if (quietTokens.isEmpty()) {
			return pushGateway.send(loudTokens, message);
		}
		if (loudTokens.isEmpty()) {
			return pushGateway.send(quietTokens, message.asSilent());
		}
		return pushGateway.send(loudTokens, message)
				.merge(pushGateway.send(quietTokens, message.asSilent()));
	}
}
