package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberRole;
import com.toy.nar.domain.member.entity.RefreshToken;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberSocialRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 로그인이 같은 계정의 다른 세션을 죽이면 안 된다.
 *
 * <p>기존엔 로그인마다 deleteByMemberId 로 그 계정의 리프레시 토큰을 전량 삭제해,
 * 다른 기기(또는 같은 기기의 이전 세션)가 다음 리프레시에서 401 → 강제 로그아웃됐다.
 * 세션을 공존시키되 상한으로 무한 증식만 막는다 — 초과분은 가장 오래된 것부터 지운다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SocialLoginServiceSessionCapTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;
	@Mock
	private NicknameGenerator nicknameGenerator;
	@Mock
	private MemberRepository memberRepository;
	@Mock
	private MemberSocialRepository memberSocialRepository;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private SocialLoginService socialLoginService;

	@BeforeEach
	void setUp() {
		socialLoginService = new SocialLoginService(
				jwtTokenProvider,
				nicknameGenerator,
				memberRepository,
				memberSocialRepository,
				refreshTokenRepository
		);
		lenient().when(jwtTokenProvider.createAccessToken(anyLong(), any(Boolean.class), any())).thenReturn("at");
		lenient().when(jwtTokenProvider.createRefreshToken(anyLong())).thenReturn("rt-new");
		lenient().when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(14));
		lenient().when(memberRepository.findById(7L)).thenReturn(Optional.of(member()));
	}

	@Test
	@DisplayName("로그인은 기존 세션을 전량 삭제하지 않는다")
	void loginKeepsExistingSessions() {
		when(refreshTokenRepository.findByMemberIdOrderByExpiresAtDesc(7L)).thenReturn(List.of(token(1)));

		socialLoginService.issueTokens(7L);

		verify(refreshTokenRepository, never()).deleteByMemberId(7L);
		verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	@DisplayName("만료 토큰은 로그인 시 청소한다")
	void loginPurgesExpiredTokens() {
		when(refreshTokenRepository.findByMemberIdOrderByExpiresAtDesc(7L)).thenReturn(List.of());

		socialLoginService.issueTokens(7L);

		verify(refreshTokenRepository).deleteExpiredByMemberId(any(Long.class), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("세션 상한(5) 도달 시 가장 오래된 것부터 지워 새 토큰 포함 5개를 유지한다")
	void loginEvictsOldestBeyondCap() {
		// expiresAt = 발급+14일 고정이라 만료 내림차순 = 최신순. index 0 이 최신.
		List<RefreshToken> five = IntStream.range(0, 5).mapToObj(this::token).toList();
		when(refreshTokenRepository.findByMemberIdOrderByExpiresAtDesc(7L)).thenReturn(five);

		socialLoginService.issueTokens(7L);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<RefreshToken>> evicted = ArgumentCaptor.forClass(Collection.class);
		verify(refreshTokenRepository).deleteAllInBatch(evicted.capture());
		// 5개 중 가장 오래된 1개만 지워 신규 발급 후 총 5개.
		assertThat(evicted.getValue()).containsExactly(five.get(4));
		verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	@DisplayName("상한 미만이면 아무것도 지우지 않는다")
	void loginBelowCapEvictsNothing() {
		List<RefreshToken> two = IntStream.range(0, 2).mapToObj(this::token).toList();
		when(refreshTokenRepository.findByMemberIdOrderByExpiresAtDesc(7L)).thenReturn(two);

		socialLoginService.issueTokens(7L);

		verify(refreshTokenRepository, never()).deleteAllInBatch(any());
	}

	private Member member() {
		Member member = Member.builder()
				.email("user@example.com")
				.name("유저")
				.tag("0001")
				.build();
		org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 7L);
		return member;
	}

	private RefreshToken token(int ageDays) {
		return RefreshToken.builder()
				.member(member())
				.token("rt-" + ageDays)
				.expiresAt(LocalDateTime.now().plusDays(14).minusDays(ageDays))
				.build();
	}
}
