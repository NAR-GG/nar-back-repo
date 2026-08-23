package com.toy.nar.app.mobile.push;

import com.toy.nar.domain.member.repository.LiveActivityCardDispatchRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 발행 이력 테이블의 인메모리 대역. UNIQUE(member_id, match_id) 와 나이 조건을 그대로 흉내낸다.
 *
 * <p>목(mock)으로 호출마다 반환값을 정해 주면 "서비스가 선점을 실제로 존중하는지" 를 검증하지
 * 못한다 — 테스트가 정답을 미리 알려 주는 셈이 된다. 상태를 들고 있는 대역을 쓰면 두 번째 호출이
 * 걸러지는 것까지 서비스 로직이 만들어 낸다.</p>
 */
class FakeLiveActivityCardDispatchRepository implements LiveActivityCardDispatchRepository {

	/** (matchId, memberId) → 발행 시각. */
	private final Map<String, Instant> dispatched = new LinkedHashMap<>();

	/** 나이 조건을 결정론적으로 시험하기 위한 시계. 기본은 "방금". */
	private Instant now = Instant.parse("2026-08-23T12:00:00Z");

	/** DB 장애를 흉내낸다. 선점 조회가 실패하면 서비스는 선점 없이 전부 보내야 한다. */
	private boolean failClaims;

	void advance(Duration amount) {
		now = now.plus(amount);
	}

	void failClaims() {
		failClaims = true;
	}

	@Override
	public List<Long> claimAll(Collection<Long> memberIds, String matchId, int setNumber) {
		if (failClaims) {
			throw new RuntimeException("db down");
		}
		List<Long> claimed = new ArrayList<>();
		for (Long memberId : memberIds) {
			if (memberId == null) {
				continue;
			}
			if (dispatched.putIfAbsent(key(matchId, memberId), now) == null) {
				claimed.add(memberId);
			}
		}
		return claimed;
	}

	@Override
	public int release(Long memberId, String matchId, Duration staleAfter) {
		Instant at = dispatched.get(key(matchId, memberId));
		if (at == null || at.isAfter(now.minus(staleAfter))) {
			return 0;
		}
		dispatched.remove(key(matchId, memberId));
		return 1;
	}

	@Override
	public int deleteAllByMatchId(String matchId) {
		int before = dispatched.size();
		dispatched.keySet().removeIf(k -> k.startsWith(matchId + "#"));
		return before - dispatched.size();
	}

	boolean hasDispatch(String matchId, Long memberId) {
		return dispatched.containsKey(key(matchId, memberId));
	}

	private static String key(String matchId, Long memberId) {
		return matchId + "#" + memberId;
	}
}
