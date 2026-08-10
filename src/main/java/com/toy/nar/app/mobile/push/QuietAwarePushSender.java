package com.toy.nar.app.mobile.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
		MobilePushResult loud = pushGateway.send(loudTokens, message);
		try {
			return loud.merge(pushGateway.send(quietTokens, message.asSilent()));
		} catch (Exception e) {
			// 무음 발송이 죽어도 이미 배달된 소리 그룹의 배달 기록을 지우면 안 된다.
			// 예외를 올리면 호출처가 전원을 FAILED 로 남겨 피드가 비고 재예약 시 중복 푸시가 된다.
			log.warn("무음 그룹 발송 실패 quietTokens={} — 소리 그룹은 배달됨", quietTokens.size(), e);
			return loud.merge(new MobilePushResult(0, quietTokens.size(), List.of(), List.of()));
		}
	}
}
