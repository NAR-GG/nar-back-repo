package com.toy.nar.app.mobile.liveactivity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.toy.nar.app.mobile.liveactivity.dto.LiveActivityTokenRequest;
import com.toy.nar.domain.member.entity.LiveActivityToken;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.LiveActivityStartTokenRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 푸시 토큰이 동시에 두 번 등록될 때 500 이 나가지 않는지 확인한다.
 *
 * <p>실제 사고: 두 요청이 모두 findByPushToken 에서 "없음"을 읽고 둘 다 insert 해
 * 진 쪽이 uk_live_activity_token_push_token 을 위반했다.</p>
 */
class LiveActivityTokenServiceTest {

	private MemberRepository memberRepository;
	private LiveActivityTokenRepository tokenRepository;
	private LiveActivityTokenService service;

	@BeforeEach
	void setUp() {
		memberRepository = mock(MemberRepository.class);
		tokenRepository = mock(LiveActivityTokenRepository.class);
		// 트랜잭션 매니저만 mock 이고 템플릿은 진짜다 — 재시도가 콜백을 다시 도는지 봐야 한다.
		service = new LiveActivityTokenService(
				memberRepository,
				tokenRepository,
				mock(LiveActivityStartTokenRepository.class),
				new TransactionTemplate(mock(PlatformTransactionManager.class)));

		given(memberRepository.findById(1L)).willReturn(Optional.of(mock(Member.class)));
	}

	@Test
	void 같은_토큰이_동시에_등록되면_재시도해_갱신으로_수습한다() {
		LiveActivityToken winner = mock(LiveActivityToken.class);
		// 첫 시도: 없음 → insert 가 unique 위반. 재시도: 이긴 행이 보인다.
		given(tokenRepository.findByPushToken("tok"))
				.willReturn(Optional.empty())
				.willReturn(Optional.of(winner));
		given(tokenRepository.save(any(LiveActivityToken.class)))
				.willThrow(new DataIntegrityViolationException("Duplicate entry 'tok'"));

		assertThatCode(() -> service.register(1L, new LiveActivityTokenRequest("match-1", "tok")))
				.doesNotThrowAnyException();

		verify(winner).reactivate(any(), any());
		verify(tokenRepository, times(1)).save(any(LiveActivityToken.class));
	}

	@Test
	void 이미_있는_토큰은_insert_하지_않는다() {
		LiveActivityToken existing = mock(LiveActivityToken.class);
		given(tokenRepository.findByPushToken("tok")).willReturn(Optional.of(existing));

		service.register(1L, new LiveActivityTokenRequest("match-1", "tok"));

		verify(existing).reactivate(any(), any());
		verify(tokenRepository, never()).save(any(LiveActivityToken.class));
	}
}
