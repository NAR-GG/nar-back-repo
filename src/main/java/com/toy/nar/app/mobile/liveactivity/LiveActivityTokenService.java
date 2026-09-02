package com.toy.nar.app.mobile.liveactivity;

import com.toy.nar.app.mobile.liveactivity.dto.LiveActivityTokenRequest;
import com.toy.nar.domain.member.entity.LiveActivityStartToken;
import com.toy.nar.domain.member.entity.LiveActivityToken;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.LiveActivityStartTokenRepository;
import com.toy.nar.domain.member.repository.LiveActivityTokenRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

// 클래스 레벨 @Transactional(readOnly = true) 를 두지 않는다 — 등록 경로가 자기 트랜잭션을
// TransactionTemplate 으로 직접 열어야 unique 위반 뒤 새 트랜잭션에서 재시도할 수 있다.
@Service
@RequiredArgsConstructor
public class LiveActivityTokenService {

	private final MemberRepository memberRepository;
	private final LiveActivityTokenRepository tokenRepository;
	private final LiveActivityStartTokenRepository startTokenRepository;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 토큰 등록 또는 갱신. 같은 토큰이 다시 오면 매치와 활성 여부만 갱신한다
	 * (앱이 카드를 내렸다가 같은 토큰으로 다시 띄우는 경우).
	 */
	public void register(Long memberId, LiveActivityTokenRequest request) {
		upsert(() -> {
			Member member = requireMember(memberId);
			tokenRepository.findByPushToken(request.pushToken())
					.ifPresentOrElse(
							token -> token.reactivate(member, request.matchId()),
							() -> tokenRepository.save(LiveActivityToken.builder()
									.member(member)
									.matchId(request.matchId())
									.pushToken(request.pushToken())
									.build()));
		});
	}

	/**
	 * push-to-start 토큰 등록 또는 갱신.
	 *
	 * <p>카드 단위 토큰과 달리 앱 단위라 매치를 받지 않는다. 이 토큰이 있어야 서버가 카드를
	 * 새로 만들 수 있다({@code Activity.pushToStartTokenUpdates}, iOS 17.2+).</p>
	 */
	public void registerStartToken(Long memberId, String pushToken) {
		upsert(() -> {
			Member member = requireMember(memberId);
			startTokenRepository.findByPushToken(pushToken)
					.ifPresentOrElse(
							token -> token.reactivate(member),
							() -> startTokenRepository.save(LiveActivityStartToken.builder()
									.member(member)
									.pushToken(pushToken)
									.build()));
		});
	}

	/**
	 * 조회 후 없으면 insert 하는 등록 경로를 트랜잭션으로 감싸고, unique 위반이면 한 번 재시도한다.
	 *
	 * <p>같은 푸시 토큰이 거의 동시에 두 번 올라오면(앱 재시도, 토큰 스트림 중복 발화) 두 요청이
	 * 모두 "없음"으로 읽고 둘 다 insert 해 진 쪽이 unique 키를 위반한다. 위반 시점엔 이긴 행이
	 * 이미 커밋돼 있으므로, 새 트랜잭션에서 다시 읽으면 갱신 경로로 흘러 정상 종료한다.
	 * 위반 난 트랜잭션은 롤백만 가능해서 같은 트랜잭션 안에서는 복구할 수 없다.</p>
	 */
	private void upsert(Runnable registration) {
		try {
			transactionTemplate.executeWithoutResult(status -> registration.run());
		} catch (DataIntegrityViolationException e) {
			// ponytail: 재시도 1회로 충분하다. 두 번째도 지려면 그 사이에 행이 지워져야 하는데 삭제 경로가 없다.
			transactionTemplate.executeWithoutResult(status -> registration.run());
		}
	}

	/** 사용자가 실시간 활동을 끄거나 로그아웃할 때 호출한다. */
	@Transactional
	public void unregisterStartToken(Long memberId, String pushToken) {
		requireMemberId(memberId);
		startTokenRepository.findByPushToken(pushToken)
				.filter(token -> token.getMember().getId().equals(memberId))
				.ifPresent(LiveActivityStartToken::deactivate);
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
