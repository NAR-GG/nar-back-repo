package com.toy.nar.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.domain.member.entity.LiveActivityStartToken;
import com.toy.nar.domain.member.entity.Member;

/**
 * push-to-start 토큰의 생존 시각 갱신.
 *
 * <p>앱은 실행할 때마다 같은 토큰을 다시 올린다. 그때 updatedAt 이 갱신되지 않으면
 * 살아있는 토큰과 iOS 로테이션으로 버려진 토큰을 구분할 수 없다. 실제로 6일 묵어 보이던
 * 토큰이 멀쩡히 카드를 받은 사례가 있었다(2026-08-17).
 */
class LiveActivityStartTokenTest {

	private LiveActivityStartToken token(Member owner) {
		LiveActivityStartToken t = LiveActivityStartToken.builder()
				.member(owner)
				.pushToken("40aaaabbbbcccc")
				.build();
		ReflectionTestUtils.setField(t, "updatedAt", LocalDateTime.now().minusDays(6));
		return t;
	}

	@Test
	@DisplayName("같은 회원이 같은 토큰을 다시 올려도 생존 시각이 갱신된다")
	void reactivateTouchesUpdatedAtEvenWhenNothingElseChanges() {
		Member owner = Member.builder().build();
		ReflectionTestUtils.setField(owner, "id", 10L);
		LiveActivityStartToken t = token(owner);
		LocalDateTime before = (LocalDateTime) ReflectionTestUtils.getField(t, "updatedAt");

		t.reactivate(owner);

		LocalDateTime after = (LocalDateTime) ReflectionTestUtils.getField(t, "updatedAt");
		assertThat(after).isAfter(before);
	}

	@Test
	@DisplayName("비활성 토큰을 다시 올리면 되살아난다")
	void reactivateRevivesDeadToken() {
		Member owner = Member.builder().build();
		ReflectionTestUtils.setField(owner, "id", 10L);
		LiveActivityStartToken t = token(owner);
		t.deactivate();

		t.reactivate(owner);

		assertThat((Boolean) ReflectionTestUtils.getField(t, "active")).isTrue();
	}
}
