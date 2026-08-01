package com.toy.nar.app.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리프레시 토큰이 발급 시각과 무관하게 항상 유니크한지 지키는 회귀 테스트.
 *
 * jti 가 없으면 페이로드가 (subject, iat, exp)뿐인데 iat/exp 는 초 단위라 같은 회원에게
 * 같은 초에 발급한 토큰이 바이트 동일했다. DB unique(token)에 걸려 동시 리프레시/로그인이
 * 500 으로 죽었다(실측 2026-07-31 새벽 14건 — 앱의 이중 401 인터셉터가 동시에 refresh 호출).
 */
class JwtTokenProviderRefreshTokenTest {

	private final JwtTokenProvider provider =
			new JwtTokenProvider("test-secret-key-must-be-long-enough-for-hs256");

	@Test
	@DisplayName("같은 회원에게 같은 초에 발급해도 토큰이 다르다")
	void 같은_초_발급_토큰이_유니크하다() {
		// 루프 안 연속 호출은 사실상 항상 같은 초에 떨어진다 — jti 가 없으면 전부 동일해진다.
		String first = provider.createRefreshToken(7L);
		String second = provider.createRefreshToken(7L);
		String third = provider.createRefreshToken(7L);

		assertThat(first).isNotEqualTo(second).isNotEqualTo(third);
		assertThat(second).isNotEqualTo(third);
	}

	@Test
	@DisplayName("jti 를 넣어도 기존 파싱(memberId 추출)이 그대로 동작한다")
	void 기존_파싱과_호환된다() {
		String token = provider.createRefreshToken(7L);

		assertThat(provider.getMemberId(token)).isEqualTo(7L);
		assertThat(provider.validate(token)).isTrue();
	}
}
