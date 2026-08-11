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

import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

	/** 계정당 동시 세션(리프레시 토큰) 상한. 초과 시 가장 오래된 세션부터 밀려난다. */
	static final int MAX_SESSIONS = 5;

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

		// 로그인마다 전량 삭제(deleteByMemberId)하면 같은 계정의 다른 기기·세션이 다음
		// 리프레시에서 401 로 강제 로그아웃된다. 세션을 공존시키되 상한으로 증식만 막는다 —
		// 만료분을 청소한 뒤 상한을 넘는 만큼 가장 오래된 것(만료 임박 순)부터 지운다.
		refreshTokenRepository.deleteExpiredByMemberId(member.getId(), java.time.LocalDateTime.now());
		java.util.List<RefreshToken> active =
				refreshTokenRepository.findByMemberIdOrderByExpiresAtDesc(member.getId());
		if (active.size() >= MAX_SESSIONS) {
			refreshTokenRepository.deleteAllInBatch(active.subList(MAX_SESSIONS - 1, active.size()));
		}
		refreshTokenRepository.save(RefreshToken.builder()
				.member(member)
				.token(refreshTokenValue)
				.expiresAt(jwtTokenProvider.getRefreshTokenExpiry())
				.build());

		return new AuthTokens(accessToken, refreshTokenValue, member.isOnboarded());
	}

	private Member createMember(SocialAccountInfo accountInfo) {
		// 같은 이메일의 기존 회원이 있으면 새 계정을 만들지 않고 소셜 계정만 연동한다.
		// (카카오/구글/네이버 모두 공급자가 검증한 이메일을 내려주므로 이메일 기준 연동이 안전)
		Member member = findLinkTargetByEmail(accountInfo.email())
				.orElseGet(() -> newMember(accountInfo));
		memberSocialRepository.save(
				MemberSocial.builder()
						.member(member)
						.provider(accountInfo.provider())
						.providerId(accountInfo.providerId())
						.build()
		);
		return member;
	}

	private Optional<Member> findLinkTargetByEmail(String email) {
		if (email == null || email.isBlank()) {
			return Optional.empty();
		}
		return memberRepository.findFirstByEmailOrderByIdAsc(email);
	}

	private Member newMember(SocialAccountInfo accountInfo) {
		NicknameGenerator.GeneratedNickname nickname = nicknameGenerator.generate();
		return memberRepository.save(
				Member.builder()
						.name(nickname.name())
						.tag(nickname.tag())
						.email(accountInfo.email())
						.build()
		);
	}
}
