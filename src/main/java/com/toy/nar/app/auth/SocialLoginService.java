package com.toy.nar.app.auth;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberSocial;
import com.toy.nar.domain.member.entity.RefreshToken;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberSocialRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
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
		String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.isOnboarded());
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
		Member member = memberRepository.save(
				Member.builder()
						.nickname(nicknameGenerator.generate())
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
