package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.dto.MemberQuietHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** 지금 알림 잠자기 구간에 있는 회원을 가려낸다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuietHoursResolver {

	private final MemberRepository memberRepository;
	private final Clock clock;

	/**
	 * [memberIds] 중 지금 잠자기 구간에 있는 회원 id.
	 *
	 * <p>조회가 실패하면 빈 집합을 준다 — 조용해지는 것보다 알림이 나가는 게 낫고,
	 * 잠자기 조회 장애가 푸시 전체를 막아선 안 된다.</p>
	 */
	public Set<Long> quietMemberIds(Collection<Long> memberIds) {
		if (memberIds.isEmpty()) {
			return Set.of();
		}
		LocalTime now = LocalTime.now(clock);
		try {
			return memberRepository.findQuietHoursByMemberIds(memberIds).stream()
					.filter(quiet -> isWithin(now, quiet.startTime(), quiet.endTime()))
					.map(MemberQuietHours::memberId)
					.collect(Collectors.toSet());
		} catch (Exception e) {
			log.warn("Failed to resolve quiet hours members={} — 전원 소리 있는 발송으로 처리", memberIds.size(), e);
			return Set.of();
		}
	}

	/** 시작 포함, 종료 제외. start > end 면 자정을 넘는 구간이다. */
	static boolean isWithin(LocalTime now, LocalTime start, LocalTime end) {
		return start.isBefore(end)
				? !now.isBefore(start) && now.isBefore(end)
				: !now.isBefore(start) || now.isBefore(end);
	}
}
