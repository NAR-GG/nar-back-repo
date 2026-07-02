package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberRole;
import com.toy.nar.domain.member.entity.MemberSocial;
import com.toy.nar.domain.member.entity.OAuthProvider;
import com.toy.nar.domain.member.entity.RefreshToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberSocialRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

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
	}

	@Test
	void loginIssuesTokensForExistingSocialMember() {
		Member member = member("existing-user@example.com", 7L);
		SocialAccountInfo accountInfo = new SocialAccountInfo(
				OAuthProvider.KAKAO,
				"12345",
				"ignored@example.com"
		);

		when(memberSocialRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "12345"))
				.thenReturn(Optional.of(MemberSocial.builder()
						.member(member)
						.provider(OAuthProvider.KAKAO)
						.providerId("12345")
						.build()));
		when(jwtTokenProvider.createAccessToken(7L, false, "USER")).thenReturn("access-token");
		when(jwtTokenProvider.createRefreshToken(7L)).thenReturn("refresh-token");
		when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(14));

		AuthTokens tokens = socialLoginService.login(accountInfo);

		assertThat(tokens.accessToken()).isEqualTo("access-token");
		assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
		assertThat(tokens.isOnboarded()).isFalse();

		verify(memberRepository, never()).save(any());
		verify(refreshTokenRepository).deleteByMemberId(7L);
		verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	void loginCreatesMemberForNewSocialAccount() {
		SocialAccountInfo accountInfo = new SocialAccountInfo(
				OAuthProvider.KAKAO,
				"67890",
				"new-user@example.com"
		);

		when(memberSocialRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "67890"))
				.thenReturn(Optional.empty());
		when(nicknameGenerator.generate())
				.thenReturn(new NicknameGenerator.GeneratedNickname("nar", "1234"));
		when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
			Member saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 11L);
			return saved;
		});
		when(jwtTokenProvider.createAccessToken(11L, false, "USER")).thenReturn("new-access-token");
		when(jwtTokenProvider.createRefreshToken(11L)).thenReturn("new-refresh-token");
		when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(14));

		AuthTokens tokens = socialLoginService.login(accountInfo);

		assertThat(tokens.accessToken()).isEqualTo("new-access-token");
		assertThat(tokens.refreshToken()).isEqualTo("new-refresh-token");
		assertThat(tokens.isOnboarded()).isFalse();

		ArgumentCaptor<MemberSocial> socialCaptor = ArgumentCaptor.forClass(MemberSocial.class);
		verify(memberSocialRepository).save(socialCaptor.capture());
		assertThat(socialCaptor.getValue().getProvider()).isEqualTo(OAuthProvider.KAKAO);
		assertThat(socialCaptor.getValue().getProviderId()).isEqualTo("67890");
		assertThat(socialCaptor.getValue().getMember().getEmail()).isEqualTo("new-user@example.com");
		verify(refreshTokenRepository).deleteByMemberId(11L);
	}

	@Test
	void loginLinksNewProviderToExistingMemberWithSameEmail() {
		// 카카오로 가입된 회원이 같은 이메일의 네이버로 로그인 → 새 계정 생성 없이 소셜만 연동
		Member existing = member("same@naver.com", 21L);
		SocialAccountInfo accountInfo = new SocialAccountInfo(
				OAuthProvider.NAVER,
				"naver-1",
				"same@naver.com"
		);

		when(memberSocialRepository.findByProviderAndProviderId(OAuthProvider.NAVER, "naver-1"))
				.thenReturn(Optional.empty());
		when(memberRepository.findFirstByEmailOrderByIdAsc("same@naver.com"))
				.thenReturn(Optional.of(existing));
		when(jwtTokenProvider.createAccessToken(21L, false, "USER")).thenReturn("linked-access");
		when(jwtTokenProvider.createRefreshToken(21L)).thenReturn("linked-refresh");
		when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(14));

		AuthTokens tokens = socialLoginService.login(accountInfo);

		assertThat(tokens.accessToken()).isEqualTo("linked-access");
		verify(memberRepository, never()).save(any()); // 새 회원 생성 없음
		ArgumentCaptor<MemberSocial> socialCaptor = ArgumentCaptor.forClass(MemberSocial.class);
		verify(memberSocialRepository).save(socialCaptor.capture());
		assertThat(socialCaptor.getValue().getMember()).isSameAs(existing);
		assertThat(socialCaptor.getValue().getProvider()).isEqualTo(OAuthProvider.NAVER);
	}

	@Test
	void loginSkipsEmailLinkingWhenEmailMissing() {
		// 이메일 미동의(null) 계정은 연동하지 않고 신규 생성
		SocialAccountInfo accountInfo = new SocialAccountInfo(OAuthProvider.KAKAO, "k-null", null);

		when(memberSocialRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "k-null"))
				.thenReturn(Optional.empty());
		when(nicknameGenerator.generate())
				.thenReturn(new NicknameGenerator.GeneratedNickname("nar", "9999"));
		when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
			Member saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 31L);
			return saved;
		});
		when(jwtTokenProvider.createAccessToken(31L, false, "USER")).thenReturn("a");
		when(jwtTokenProvider.createRefreshToken(31L)).thenReturn("r");
		when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(14));

		socialLoginService.login(accountInfo);

		verify(memberRepository, never()).findFirstByEmailOrderByIdAsc(any());
		verify(memberRepository).save(any(Member.class));
	}

	@Test
	void findAdminMemberReturnsAdmin() {
		Member member = member("admin@example.com", 2L);
		ReflectionTestUtils.setField(member, "role", MemberRole.ADMIN);
		SocialAccountInfo info = new SocialAccountInfo(OAuthProvider.GOOGLE, "g-1", "admin@example.com");
		when(memberSocialRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "g-1"))
				.thenReturn(Optional.of(MemberSocial.builder()
						.member(member).provider(OAuthProvider.GOOGLE).providerId("g-1").build()));

		assertThat(socialLoginService.findAdminMember(info)).isSameAs(member);
	}

	@Test
	void findAdminMemberRejectsNonAdmin() {
		Member member = member("user@example.com", 3L); // 기본 role USER
		SocialAccountInfo info = new SocialAccountInfo(OAuthProvider.GOOGLE, "g-2", "user@example.com");
		when(memberSocialRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "g-2"))
				.thenReturn(Optional.of(MemberSocial.builder()
						.member(member).provider(OAuthProvider.GOOGLE).providerId("g-2").build()));

		assertThatThrownBy(() -> socialLoginService.findAdminMember(info))
				.isInstanceOf(OAuth2AuthenticationException.class);
		verify(memberRepository, never()).save(any());
	}

	@Test
	void findAdminMemberRejectsUnknownAccountWithoutCreating() {
		SocialAccountInfo info = new SocialAccountInfo(OAuthProvider.GOOGLE, "g-x", "nobody@example.com");
		when(memberSocialRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "g-x"))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> socialLoginService.findAdminMember(info))
				.isInstanceOf(OAuth2AuthenticationException.class);
		verify(memberRepository, never()).save(any());
		verify(memberSocialRepository, never()).save(any());
	}

	private Member member(String email, Long id) {
		Member member = Member.builder()
				.name("nar-user").tag("0000")
				.email(email)
				.build();
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
