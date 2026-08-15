package com.toy.nar.app.mobile.notification;

import com.toy.nar.app.mobile.notification.dto.TeamNotificationUpdateRequest;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberTeamNotificationSubscription;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberTeamNotificationSubscriptionRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MobileTeamNotificationServiceTest {

	private MemberRepository memberRepository;
	private TeamRepository teamRepository;
	private MemberTeamNotificationSubscriptionRepository subscriptionRepository;
	private MobileTeamNotificationService service;

	@BeforeEach
	void setUp() {
		memberRepository = mock(MemberRepository.class);
		teamRepository = mock(TeamRepository.class);
		subscriptionRepository = mock(MemberTeamNotificationSubscriptionRepository.class);
		service = new MobileTeamNotificationService(
				memberRepository, teamRepository, subscriptionRepository,
				mock(com.toy.nar.app.mobile.push.LiveActivityCatchUpService.class));
	}

	@Test
	void returnsFavoriteTeamFirstThenLckOrder() {
		Team t1 = team(1L, "T1", "T1");
		Team dns = team(2L, "DN SOOPers", "DNS");
		Member member = member(7L, dns);
		MemberTeamNotificationSubscription t1Subscription = subscription(member, t1);
		MemberTeamNotificationSubscription dnsSubscription = subscription(member, dns);
		dnsSubscription.update(false, true, true);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(subscriptionRepository.findByMember_Id(7L))
				.thenReturn(List.of(t1Subscription, dnsSubscription));

		var response = service.getSubscriptions(7L);

		assertThat(response).extracting(item -> item.teamCode())
				.containsExactly("DNS", "T1");
		assertThat(response.get(0).favoriteTeam()).isTrue();
		assertThat(response.get(0).setStartEnabled()).isFalse();
		assertThat(response.get(0).liveEventEnabled()).isTrue();
	}

	@Test
	void returnsAvailableTeamsWithSubscribedFirstThenCatalogOrder() {
		Team t1 = team(1L, "T1", "T1");
		Team gen = team(2L, "Gen.G", "GEN");
		Member member = member(7L, t1);
		MemberTeamNotificationSubscription subscription = subscription(member, gen);
		subscription.update(false, false, true);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(subscriptionRepository.findByMember_Id(7L)).thenReturn(List.of(subscription));
		when(teamRepository.findAllByCodeIn(any())).thenReturn(List.of(gen, t1));

		var response = service.getAvailableTeams(7L);

		// 구독 중인 GEN 이 카탈로그상 T1 보다 뒤여도 최상단에 노출된다.
		assertThat(response).extracting(item -> item.teamCode())
				.containsExactly("GEN", "T1");
		assertThat(response.get(0).subscribed()).isTrue();
		assertThat(response.get(0).liveEventEnabled()).isTrue();
		assertThat(response.get(1).subscribed()).isFalse();
		assertThat(response.get(1).setStartEnabled()).isTrue();
		assertThat(response.get(1).setEndEnabled()).isTrue();
		assertThat(response.get(1).liveEventEnabled()).isFalse();
	}

	@Test
	void subscribesWithDefaultsAndReturnsExistingSubscriptionIdempotently() {
		Team t1 = team(1L, "T1", "T1");
		Member member = member(7L, t1);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(teamRepository.findById(1L)).thenReturn(Optional.of(t1));
		when(subscriptionRepository.findByMember_IdAndTeam_Id(7L, 1L))
				.thenReturn(Optional.empty());
		when(subscriptionRepository.save(any(MemberTeamNotificationSubscription.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var created = service.subscribe(7L, 1L);

		assertThat(created.subscribed()).isTrue();
		assertThat(created.setStartEnabled()).isTrue();
		assertThat(created.setEndEnabled()).isTrue();
		assertThat(created.liveEventEnabled()).isFalse();

		MemberTeamNotificationSubscription existing = subscription(member, t1);
		when(subscriptionRepository.findByMember_IdAndTeam_Id(7L, 1L))
				.thenReturn(Optional.of(existing));

		var repeated = service.subscribe(7L, 1L);

		assertThat(repeated.teamId()).isEqualTo(1L);
		verify(subscriptionRepository).save(any(MemberTeamNotificationSubscription.class));
	}

	@Test
	void rejectsTeamsOutsideLckCatalog() {
		Team lecTeam = team(99L, "G2 Esports", "G2");
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, null)));
		when(teamRepository.findById(99L)).thenReturn(Optional.of(lecTeam));

		assertThatThrownBy(() -> service.subscribe(7L, 99L))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("400 BAD_REQUEST");

		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void returnsNotFoundForMissingTeamAndMissingSubscription() {
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, null)));
		when(teamRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.subscribe(7L, 404L))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404 NOT_FOUND");

		when(subscriptionRepository.findByMember_IdAndTeam_Id(7L, 1L))
				.thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.update(
				7L, 1L, new TeamNotificationUpdateRequest(true, true, false)))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404 NOT_FOUND");
	}

	@Test
	void updatesAndDeletesFavoriteTeamSubscriptionWithoutTouchingOthers() {
		Team t1 = team(1L, "T1", "T1");
		Member member = member(7L, t1);
		MemberTeamNotificationSubscription subscription = subscription(member, t1);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(subscriptionRepository.findByMember_IdAndTeam_Id(7L, 1L))
				.thenReturn(Optional.of(subscription));

		var updated = service.update(7L, 1L, new TeamNotificationUpdateRequest(false, true, true));
		service.delete(7L, 1L);

		assertThat(updated.favoriteTeam()).isTrue();
		assertThat(updated.setStartEnabled()).isFalse();
		assertThat(updated.setEndEnabled()).isTrue();
		assertThat(updated.liveEventEnabled()).isTrue();
		verify(subscriptionRepository).delete(subscription);
	}

	@Test
	void requiresAuthenticationAndAddsOnboardingTeamWithoutRemovingExistingSubscriptions() {
		assertThatThrownBy(() -> service.getSubscriptions(null))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("401 UNAUTHORIZED");

		Team t1 = team(1L, "T1", "T1");
		Member member = member(7L, t1);
		when(subscriptionRepository.findByMember_IdAndTeam_Id(7L, 1L))
				.thenReturn(Optional.empty());
		when(subscriptionRepository.save(any(MemberTeamNotificationSubscription.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		service.ensureDefaultSubscription(member, t1);

		MemberTeamNotificationSubscription existing = subscription(member, t1);
		when(subscriptionRepository.findByMember_IdAndTeam_Id(7L, 1L))
				.thenReturn(Optional.of(existing));
		service.ensureDefaultSubscription(member, t1);

		verify(subscriptionRepository, times(1)).save(any(MemberTeamNotificationSubscription.class));
		verify(subscriptionRepository, never()).delete(any());
	}

	private MemberTeamNotificationSubscription subscription(Member member, Team team) {
		return new MemberTeamNotificationSubscription(member, team);
	}

	private Member member(Long id, Team favoriteTeam) {
		Member member = Member.builder().name("용맹한바론").tag("0000").email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", id);
		if (favoriteTeam != null) {
			member.completeOnboarding("LCK", favoriteTeam, List.of());
		}
		return member;
	}

	private Team team(Long id, String name, String code) {
		Team team = Team.builder().name(name).code(code).imageUrl(code + ".png").build();
		ReflectionTestUtils.setField(team, "id", id);
		return team;
	}
}
