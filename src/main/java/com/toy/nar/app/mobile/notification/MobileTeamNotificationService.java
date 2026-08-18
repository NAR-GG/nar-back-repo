package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.mobile.notification.dto.TeamNotificationSubscriptionResponse;
import com.toy.nar.app.mobile.notification.dto.TeamNotificationUpdateRequest;
import com.toy.nar.app.mobile.push.LiveActivityCatchUpService;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberTeamNotificationSubscription;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberTeamNotificationSubscriptionRepository;
import com.toy.nar.domain.participant.LckTeamCatalog;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileTeamNotificationService {

	private final MemberRepository memberRepository;
	private final TeamRepository teamRepository;
	private final MemberTeamNotificationSubscriptionRepository subscriptionRepository;
	private final LiveActivityCatchUpService liveActivityCatchUpService;

	public List<TeamNotificationSubscriptionResponse> getSubscriptions(Long memberId) {
		Member member = requireMember(memberId);
		return subscriptionRepository.findByMember_Id(memberId).stream()
				.sorted(subscriptionComparator(member))
				.map(subscription -> toResponse(member, subscription))
				.toList();
	}

	public List<TeamNotificationSubscriptionResponse> getAvailableTeams(Long memberId) {
		Member member = requireMember(memberId);
		Map<Long, MemberTeamNotificationSubscription> subscriptions = subscriptionRepository.findByMember_Id(memberId)
				.stream()
				.collect(Collectors.toMap(subscription -> subscription.getTeam().getId(), Function.identity()));
		Map<String, Team> teamsByCode = teamRepository.findAllByCodeIn(LckTeamCatalog.TEAM_CODES).stream()
				.filter(team -> team.getCode() != null)
				.collect(Collectors.toMap(
						team -> team.getCode().toUpperCase(Locale.ROOT),
						Function.identity(),
						(first, ignored) -> first));

		return LckTeamCatalog.TEAM_CODES.stream()
				.map(teamsByCode::get)
				.filter(team -> team != null)
				.map(team -> {
					MemberTeamNotificationSubscription subscription = subscriptions.get(team.getId());
					return subscription == null
							? defaultResponse(member, team)
							: toResponse(member, subscription);
				})
				// 구독 중인 팀을 최상단에 올리고, 그 안에서는 리그 화면 순서(catalog) 유지(안정 정렬).
				.sorted(Comparator.comparing((TeamNotificationSubscriptionResponse response) -> !response.subscribed()))
				.toList();
	}

	@Transactional
	public TeamNotificationSubscriptionResponse subscribe(Long memberId, Long teamId) {
		Member member = requireMember(memberId);
		Team team = requireLckTeam(teamId);
		boolean[] created = { false };
		MemberTeamNotificationSubscription subscription = subscriptionRepository
				.findByMember_IdAndTeam_Id(memberId, teamId)
				.orElseGet(() -> {
					created[0] = true;
					return subscriptionRepository.save(
							new MemberTeamNotificationSubscription(member, team));
				});
		// 새로 구독한 경우에만 따라잡는다. 이미 구독 중이면 카드는 이미 받았거나 사용자가 지운 것이다.
		if (created[0] && subscription.isSetStartEnabled()) {
			liveActivityCatchUpService.catchUpTeam(memberId, team.getCode());
		}
		return toResponse(member, subscription);
	}

	@Transactional
	public TeamNotificationSubscriptionResponse update(
			Long memberId,
			Long teamId,
			TeamNotificationUpdateRequest request) {
		Member member = requireMember(memberId);
		MemberTeamNotificationSubscription subscription = subscriptionRepository
				.findByMember_IdAndTeam_Id(memberId, teamId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "팀 알림 구독을 찾을 수 없습니다."));
		boolean turnedOn = !subscription.isSetStartEnabled() && request.setStartEnabled();
		subscription.update(
				request.setStartEnabled(),
				request.setEndEnabled(),
				request.liveEventEnabled());
		subscription.updateLiveEventTypes(
				request.killEnabled(),
				request.baronEnabled(),
				request.dragonEnabled(),
				request.towerEnabled(),
				request.inhibitorEnabled());
		// 껐다 켠 것도 "이제부터 보겠다"는 의사표시다. 켜는 전환에서만 따라잡아 재호출로 카드가 쌓이지 않게 한다.
		if (turnedOn) {
			liveActivityCatchUpService.catchUpTeam(memberId, subscription.getTeam().getCode());
		}
		return toResponse(member, subscription);
	}

	@Transactional
	public void delete(Long memberId, Long teamId) {
		requireMember(memberId);
		MemberTeamNotificationSubscription subscription = subscriptionRepository
				.findByMember_IdAndTeam_Id(memberId, teamId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "팀 알림 구독을 찾을 수 없습니다."));
		subscriptionRepository.delete(subscription);
	}

	@Transactional
	public void ensureDefaultSubscription(Member member, Team team) {
		if (member == null || member.getId() == null || team == null) {
			return;
		}
		subscriptionRepository.findByMember_IdAndTeam_Id(member.getId(), team.getId())
				.orElseGet(() -> subscriptionRepository.save(
						new MemberTeamNotificationSubscription(member, team)));
	}

	private Member requireMember(Long memberId) {
		if (memberId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
	}

	private Team requireLckTeam(Long teamId) {
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "팀을 찾을 수 없습니다."));
		if (!LckTeamCatalog.contains(team.getCode())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "구독할 수 없는 팀입니다.");
		}
		return team;
	}

	private Comparator<MemberTeamNotificationSubscription> subscriptionComparator(Member member) {
		Long favoriteTeamId = favoriteTeamId(member);
		return Comparator
				.comparing((MemberTeamNotificationSubscription subscription) ->
						!subscription.getTeam().getId().equals(favoriteTeamId))
				.thenComparingInt(subscription -> LckTeamCatalog.orderOf(subscription.getTeam().getCode()))
				.thenComparing(subscription -> subscription.getTeam().getName());
	}

	private TeamNotificationSubscriptionResponse toResponse(
			Member member,
			MemberTeamNotificationSubscription subscription) {
		Team team = subscription.getTeam();
		return new TeamNotificationSubscriptionResponse(
				team.getId(),
				team.getCode(),
				team.getName(),
				team.getImageUrl(),
				team.getId().equals(favoriteTeamId(member)),
				true,
				subscription.isSetStartEnabled(),
				subscription.isSetEndEnabled(),
				subscription.isLiveEventEnabled(),
				subscription.isKillEnabled(),
				subscription.isBaronEnabled(),
				subscription.isDragonEnabled(),
				subscription.isTowerEnabled(),
				subscription.isInhibitorEnabled());
	}

	private TeamNotificationSubscriptionResponse defaultResponse(Member member, Team team) {
		return new TeamNotificationSubscriptionResponse(
				team.getId(),
				team.getCode(),
				team.getName(),
				team.getImageUrl(),
				team.getId().equals(favoriteTeamId(member)),
				false,
				true,
				true,
				false,
				true,
				true,
				true,
				true,
				true);
	}

	private Long favoriteTeamId(Member member) {
		return member.getFavoriteTeam() == null ? null : member.getFavoriteTeam().getId();
	}
}
