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
 * <p>잠자기 시간대 회원에게는 <b>푸시를 아예 보내지 않고</b> 건너뛴다. 소리만 죽여 OS 알림창에
 * 조용히 쌓는 방식도 가능했지만, 아침에 알림창이 밤 알림으로 도배되는 게 문의 원문("밤에도
 * 너무 많이 온다")과 어긋난다. 알림함(마이구독 피드)에는 그대로 기록되므로 앱을 열면 다 보인다.</p>
 *
 * <p>보낼 대상은 한 번에 몰아 보낸다. 회원별로 쪼개지 않는다 — 2026-08-04 프로덕션에서 구독자
 * 1,502명 개별 발송이 472초 걸려 솔랭 폴 스레드를 통째로 막은 적이 있다.</p>
 */
@Component
@RequiredArgsConstructor
public class QuietAwarePushSender {

	private final MobilePushGateway pushGateway;
	private final QuietHoursResolver quietHoursResolver;

	/**
	 * 발송 결과.
	 *
	 * @param result           실제로 발송한 건의 결과. 건너뛴 회원은 여기 없다.
	 * @param skippedMemberIds 잠자기라 발송하지 않은 회원. 호출처가 알림함에만 기록하고
	 *                         재예약되지 않게 마감해야 한다.
	 */
	public record Outcome(MobilePushResult result, List<Long> skippedMemberIds) {

		/** 건너뛴 회원인지. 호출처가 발송 성공/실패를 가릴 때 이들을 제외하는 데 쓴다. */
		public boolean isSkipped(Long memberId) {
			return skippedMemberIds.contains(memberId);
		}
	}

	public Outcome send(Map<Long, List<String>> tokensByMember, MobilePushMessage message) {
		Set<Long> quietMemberIds = quietHoursResolver.quietMemberIds(tokensByMember.keySet());

		List<Long> skipped = new ArrayList<>();
		List<String> sendTokens = new ArrayList<>();
		tokensByMember.forEach((memberId, tokens) -> {
			if (quietMemberIds.contains(memberId)) {
				skipped.add(memberId);
			} else {
				sendTokens.addAll(tokens);
			}
		});

		if (sendTokens.isEmpty()) {
			return new Outcome(
					new MobilePushResult(0, 0, List.of(), List.of()),
					List.copyOf(skipped));
		}
		return new Outcome(pushGateway.send(sendTokens, message), List.copyOf(skipped));
	}
}
