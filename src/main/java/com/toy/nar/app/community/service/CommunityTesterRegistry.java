package com.toy.nar.app.community.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 테스트 글(status=TEST)을 볼 수 있는 회원. prod 에서 새 기능을 실제 환경으로
 * 확인하면서 일반 사용자 목록은 건드리지 않으려는 장치다.
 *
 * <p>이메일을 코드에 박지 않고 <b>회원 id 를 env</b>({@code COMMUNITY_TESTER_MEMBER_IDS})
 * 로 받는다 — 테스터가 바뀔 때 재배포만 하면 되고, 코드에 개인정보가 남지 않는다.
 * 비어 있으면(기본) 아무도 테스트 글을 볼 수 없다 = 실수로 노출될 여지가 없다.</p>
 */
@Component
public class CommunityTesterRegistry {

	private final Set<Long> testerMemberIds;

	public CommunityTesterRegistry(@Value("${community.tester-member-ids:}") String rawIds) {
		this.testerMemberIds = rawIds == null || rawIds.isBlank()
				? Set.of()
				: java.util.Arrays.stream(rawIds.split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.map(Long::valueOf)
						.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public boolean isTester(Long memberId) {
		return memberId != null && testerMemberIds.contains(memberId);
	}
}
