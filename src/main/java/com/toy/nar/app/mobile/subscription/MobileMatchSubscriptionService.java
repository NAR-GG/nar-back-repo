package com.toy.nar.app.mobile.subscription;

import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.push.LiveActivityCatchUpService;
import com.toy.nar.app.mobile.subscription.dto.MatchNotificationToggles;
import com.toy.nar.app.mobile.subscription.dto.MatchSubscriptionResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberMatchSubscription;
import com.toy.nar.domain.member.repository.MemberMatchSubscriptionRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 경기 예약 알림 구독. 팀 구독과 별개로 특정 경기를 구독하면
 * 그 경기의 세트 시작/종료/라이브 이벤트를 받는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileMatchSubscriptionService {

	private final MemberMatchSubscriptionRepository subscriptionRepository;
	private final MemberRepository memberRepository;
	private final LeagueMatchRepository leagueMatchRepository;
	private final LiveActivityCatchUpService liveActivityCatchUpService;

	/** 내가 구독한 경기 matchId 목록. 앱이 리스트에서 벨 상태를 그리는 데 쓴다. */
	public List<String> getSubscribedMatchIds(Long memberId) {
		return subscriptionRepository.findMatchIdsByMemberId(memberId);
	}

	@Transactional
	public void subscribe(Long memberId, String matchId, MatchNotificationToggles toggles) {
		if (matchId == null || matchId.isBlank()) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		if (!leagueMatchRepository.existsById(matchId)) {
			throw new CustomException(ErrorCode.DATA_NOT_FOUND);
		}
		// 이미 구독 중이면 조용히 통과(멱등). 유니크 제약이 중복 삽입을 막는다.
		if (subscriptionRepository.existsByMemberIdAndMatchId(memberId, matchId)) {
			return;
		}
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new CustomException(ErrorCode.DATA_NOT_FOUND));
		MemberMatchSubscription subscription = new MemberMatchSubscription(
				member, matchId,
				toggles.setStartOrTrue(), toggles.setEndOrTrue(), toggles.liveEventOrTrue());
		subscription.updateToggles(
				null, null, null,
				toggles.killEnabled(), toggles.baronEnabled(), toggles.dragonEnabled(),
				toggles.towerEnabled(), toggles.inhibitorEnabled());
		subscriptionRepository.save(subscription);
		if (toggles.setStartOrTrue()) {
			// 진행 중인 경기를 지금 구독했으면 잠금화면 카드는 다음 세트까지 안 뜬다 — 따라잡는다.
			liveActivityCatchUpService.catchUpMatch(memberId, matchId);
		}
	}

	/**
	 * 구독을 유지한 채 알림 토글만 바꾼다. 지금까지 경로가 구독/해제뿐이라 앱이 토글 하나를
	 * 끄려면 해제 후 재구독해야 했는데, 그러면 진행 중인 경기에서 카드가 다시 만들어진다.
	 */
	/** 경기 한 건의 알림 토글 상태. 구독 중이 아니면 기본값을 돌려준다. */
	public MatchSubscriptionResponse getSubscription(Long memberId, String matchId) {
		return subscriptionRepository.findByMemberIdAndMatchId(memberId, matchId)
				.map(MatchSubscriptionResponse::from)
				.orElseGet(() -> MatchSubscriptionResponse.notSubscribed(matchId));
	}

	@Transactional
	public void updateToggles(Long memberId, String matchId, MatchNotificationToggles toggles) {
		MemberMatchSubscription subscription = subscriptionRepository
				.findByMemberIdAndMatchId(memberId, matchId)
				.orElseThrow(() -> new CustomException(ErrorCode.DATA_NOT_FOUND));
		subscription.updateToggles(
				toggles.setStartEnabled(), toggles.setEndEnabled(), toggles.liveEventEnabled(),
				toggles.killEnabled(), toggles.baronEnabled(), toggles.dragonEnabled(),
				toggles.towerEnabled(), toggles.inhibitorEnabled());
	}

	@Transactional
	public void unsubscribe(Long memberId, String matchId) {
		subscriptionRepository.deleteByMemberIdAndMatchId(memberId, matchId);
	}
}
