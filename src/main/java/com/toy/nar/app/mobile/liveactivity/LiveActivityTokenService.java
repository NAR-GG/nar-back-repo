package com.toy.nar.app.mobile.liveactivity;

import com.toy.nar.app.mobile.liveactivity.dto.LiveActivityTokenRequest;
import com.toy.nar.domain.member.entity.LiveActivityToken;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveActivityTokenService {

	private final MemberRepository memberRepository;
	private final LiveActivityTokenRepository tokenRepository;

	/**
	 * 토큰 등록 또는 갱신. 같은 토큰이 다시 오면 매치와 활성 여부만 갱신한다
	 * (앱이 카드를 내렸다가 같은 토큰으로 다시 띄우는 경우).
	 */
	@Transactional
	public void register(Long memberId, LiveActivityTokenRequest request) {
		Member member = requireMember(memberId);
		tokenRepository.findByPushToken(request.pushToken())
				.ifPresentOrElse(
						token -> token.reactivate(member, request.matchId()),
						() -> tokenRepository.save(LiveActivityToken.builder()
								.member(member)
								.matchId(request.matchId())
								.pushToken(request.pushToken())
								.build()));
	}

	/**
	 * 앱이 카드를 내렸을 때 호출한다. 매치가 끝나면 서버도 정리하지만,
	 * 사용자가 직접 카드를 지우는 경로는 앱만 안다.
	 */
	@Transactional
	public void unregister(Long memberId, String pushToken) {
		requireMemberId(memberId);
		tokenRepository.findByPushToken(pushToken)
				.filter(token -> token.getMember().getId().equals(memberId))
				.ifPresent(LiveActivityToken::deactivate);
	}

	private Member requireMember(Long memberId) {
		requireMemberId(memberId);
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
	}

	private void requireMemberId(Long memberId) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
	}
}
