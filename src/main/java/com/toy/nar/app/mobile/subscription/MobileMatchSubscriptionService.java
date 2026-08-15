package com.toy.nar.app.mobile.subscription;

import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.push.LiveActivityCatchUpService;
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
	public void subscribe(Long memberId, String matchId,
			boolean setStartEnabled, boolean setEndEnabled, boolean liveEventEnabled) {
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
		subscriptionRepository.save(new MemberMatchSubscription(
				member, matchId, setStartEnabled, setEndEnabled, liveEventEnabled));
		if (setStartEnabled) {
			// 진행 중인 경기를 지금 구독했으면 잠금화면 카드는 다음 세트까지 안 뜬다 — 따라잡는다.
			liveActivityCatchUpService.catchUpMatch(memberId, matchId);
		}
	}

	@Transactional
	public void unsubscribe(Long memberId, String matchId) {
		subscriptionRepository.deleteByMemberIdAndMatchId(memberId, matchId);
	}
}
