package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberRole;
import com.toy.nar.domain.member.entity.MemberSocial;
import com.toy.nar.domain.member.entity.RefreshToken;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberSocialRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

	private final JwtTokenProvider jwtTokenProvider;
	private final NicknameGenerator nicknameGenerator;
	private final MemberRepository memberRepository;
	private final MemberSocialRepository memberSocialRepository;
	private final RefreshTokenRepository refreshTokenRepository;

	@Transactional
	public Member findOrCreateMember(SocialAccountInfo accountInfo) {
		return memberSocialRepository
				.findByProviderAndProviderId(accountInfo.provider(), accountInfo.providerId())
				.map(MemberSocial::getMember)
				.orElseGet(() -> createMember(accountInfo));
	}

	/**
	 * 백오피스 로그인 전용. 기존 회원이면서 ADMIN 인 경우만 통과. 회원을 생성하지 않는다.
	 * 미등록/비ADMIN 이면 OAuth2AuthenticationException → 실패 핸들러로.
	 */
	@Transactional(readOnly = true)
	public Member findAdminMember(SocialAccountInfo accountInfo) {
		Member member = memberSocialRepository
				.findByProviderAndProviderId(accountInfo.provider(), accountInfo.providerId())
				.map(MemberSocial::getMember)
				.orElseThrow(() -> new OAuth2AuthenticationException(
						new OAuth2Error("access_denied"), "백오피스에 등록되지 않은 계정입니다"));
		if (member.getRole() != MemberRole.ADMIN) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error("access_denied"), "관리자 권한이 없습니다");
		}
		return member;
	}

	@Transactional
	public AuthTokens login(SocialAccountInfo accountInfo) {
		Member member = findOrCreateMember(accountInfo);
		return issueTokens(member);
	}

	@Transactional
	public AuthTokens issueTokens(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "회원을 찾을 수 없습니다"));
		return issueTokens(member);
	}

	private AuthTokens issueTokens(Member member) {
		String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.isOnboarded(), member.getRole().name());
		String refreshTokenValue = jwtTokenProvider.createRefreshToken(member.getId());

		refreshTokenRepository.deleteByMemberId(member.getId());
		refreshTokenRepository.save(RefreshToken.builder()
				.member(member)
				.token(refreshTokenValue)
				.expiresAt(jwtTokenProvider.getRefreshTokenExpiry())
				.build());

		return new AuthTokens(accessToken, refreshTokenValue, member.isOnboarded());
	}

	private Member createMember(SocialAccountInfo accountInfo) {
		NicknameGenerator.GeneratedNickname nickname = nicknameGenerator.generate();
		Member member = memberRepository.save(
				Member.builder()
						.name(nickname.name())
						.tag(nickname.tag())
						.email(accountInfo.email())
						.build()
		);
		memberSocialRepository.save(
				MemberSocial.builder()
						.member(member)
						.provider(accountInfo.provider())
						.providerId(accountInfo.providerId())
						.build()
		);
		return member;
	}
}
