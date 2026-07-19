package com.toy.nar.app.mobile.subscription;

import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionPageResponse;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionResponse;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberFavoritePlayer;
import com.toy.nar.domain.member.repository.MemberFavoritePlayerRepository;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobilePlayerSubscriptionServiceTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private MemberFavoritePlayerRepository subscriptionRepository;

	@Mock
	private PlayerRepository playerRepository;

	private MobilePlayerSubscriptionService service;

	@BeforeEach
	void setUp() {
		service = new MobilePlayerSubscriptionService(
				memberRepository,
				subscriptionRepository,
				playerRepository);
	}

	@Test
	void returnsOnboardingFavoritePlayersAsSubscriptions() {
		Member member = member(7L);
		Player faker = player(10L, "Faker");
		MemberFavoritePlayer subscription = MemberFavoritePlayer.builder()
				.member(member)
				.player(faker)
				.build();
		PlayerRepository.LckPlayerOption option = option(10L, "Faker", 1L, "T1", "T1");

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(subscriptionRepository.findAllByMember_Id(7L)).thenReturn(List.of(subscription));
		when(playerRepository.findLckPlayerOptionsByPlayerIds("LCK", 2026, Set.of(10L)))
				.thenReturn(List.of(option));

		List<PlayerSubscriptionResponse> response = service.getSubscriptions(7L);

		assertThat(response).singleElement().satisfies(result -> {
			assertThat(result.playerId()).isEqualTo(10L);
			assertThat(result.playerName()).isEqualTo("Faker");
			assertThat(result.teamCode()).isEqualTo("T1");
			assertThat(result.subscribed()).isTrue();
		});
	}

	@Test
	void searchesAvailableLckPlayersAndMarksSubscriptions() {
		Member member = member(7L);
		PlayerRepository.LckPlayerOption faker = option(10L, "Faker", 1L, "T1", "T1");
		PlayerRepository.LckPlayerOption keria = option(11L, "Keria", 1L, "T1", "T1");
		PageRequest pageRequest = PageRequest.of(0, 20);

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(subscriptionRepository.findPlayerIdsByMemberId(7L)).thenReturn(Set.of(10L));
		when(playerRepository.findLckPlayerOptions("LCK", 2026, 1L, "a", pageRequest))
				.thenReturn(new PageImpl<>(List.of(faker, keria), pageRequest, 2));

		PlayerSubscriptionPageResponse response = service.getAvailablePlayers(7L, "a", 1L, 0, 20);

		assertThat(response.content()).hasSize(2);
		assertThat(response.content().get(0).subscribed()).isTrue();
		assertThat(response.content().get(1).subscribed()).isFalse();
		assertThat(response.totalElements()).isEqualTo(2);
	}

	@Test
	void subscribesLckPlayerAndReturnsExistingSubscriptionIdempotently() {
		Member member = member(7L);
		Player faker = player(10L, "Faker");
		PlayerRepository.LckPlayerOption option = option(10L, "Faker", 1L, "T1", "T1");

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(playerRepository.findById(10L)).thenReturn(Optional.of(faker));
		when(playerRepository.findLckPlayerOption("LCK", 2026, 10L)).thenReturn(List.of(option));
		when(subscriptionRepository.findByMember_IdAndPlayer_Id(7L, 10L)).thenReturn(Optional.empty());
		when(subscriptionRepository.save(any(MemberFavoritePlayer.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		PlayerSubscriptionResponse created = service.subscribe(7L, 10L);

		assertThat(created.subscribed()).isTrue();
		verify(subscriptionRepository).save(any(MemberFavoritePlayer.class));

		MemberFavoritePlayer existing = MemberFavoritePlayer.builder().member(member).player(faker).build();
		when(subscriptionRepository.findByMember_IdAndPlayer_Id(7L, 10L)).thenReturn(Optional.of(existing));

		PlayerSubscriptionResponse duplicated = service.subscribe(7L, 10L);

		assertThat(duplicated.playerId()).isEqualTo(10L);
		verify(subscriptionRepository).save(any(MemberFavoritePlayer.class));
	}

	@Test
	void rejectsPlayerOutsideCurrentLckRoster() {
		Member member = member(7L);
		Player player = player(99L, "Unknown");

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(playerRepository.findById(99L)).thenReturn(Optional.of(player));
		when(playerRepository.findLckPlayerOption("LCK", 2026, 99L)).thenReturn(List.of());
		when(playerRepository.findSoloRankPlayerOptionsByPlayerIds(Set.of(99L))).thenReturn(List.of());

		assertThatThrownBy(() -> service.subscribe(7L, 99L))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("구독 가능한");

		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void deletesOnlyRequestedSubscription() {
		MemberFavoritePlayer subscription = MemberFavoritePlayer.builder()
				.member(member(7L))
				.player(player(10L, "Faker"))
				.build();
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L)));
		when(subscriptionRepository.findByMember_IdAndPlayer_Id(7L, 10L))
				.thenReturn(Optional.of(subscription));

		service.delete(7L, 10L);

		verify(subscriptionRepository).delete(subscription);
	}

	@Test
	void requiresAuthentication() {
		assertThatThrownBy(() -> service.getSubscriptions(null))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("로그인");
	}

	@Test
	@DisplayName("솔랭 전용 구독 선수(2026 LCK 미출전)도 getSubscriptions에 표시된다")
	void showsSoloRankOnlySubscription() {
		Member member = member(7L);
		Player deft = player(9L, "Deft");
		MemberFavoritePlayer sub = MemberFavoritePlayer.builder().member(member).player(deft).build();
		PlayerRepository.LckPlayerOption soloOption = option(9L, "Deft", null, null, null);

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(subscriptionRepository.findAllByMember_Id(7L)).thenReturn(List.of(sub));
		when(playerRepository.findLckPlayerOptionsByPlayerIds("LCK", 2026, Set.of(9L)))
				.thenReturn(List.of());
		when(playerRepository.findSoloRankPlayerOptionsByPlayerIds(Set.of(9L)))
				.thenReturn(List.of(soloOption));

		List<PlayerSubscriptionResponse> response = service.getSubscriptions(7L);

		assertThat(response).extracting(PlayerSubscriptionResponse::playerName).containsExactly("Deft");
	}

	@Test
	@DisplayName("subscribe: LCK 옵션 없어도 솔랭 전용 옵션이 있으면 구독 성공")
	void subscribesSoloRankOnlyPlayer() {
		Member member = member(7L);
		Player deft = player(9L, "Deft");
		PlayerRepository.LckPlayerOption soloOption = option(9L, "Deft", null, null, null);

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(playerRepository.findById(9L)).thenReturn(Optional.of(deft));
		when(playerRepository.findLckPlayerOption("LCK", 2026, 9L)).thenReturn(List.of());
		when(playerRepository.findSoloRankPlayerOptionsByPlayerIds(Set.of(9L)))
				.thenReturn(List.of(soloOption));
		when(subscriptionRepository.findByMember_IdAndPlayer_Id(7L, 9L)).thenReturn(Optional.empty());
		when(subscriptionRepository.save(any(MemberFavoritePlayer.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		PlayerSubscriptionResponse response = service.subscribe(7L, 9L);

		assertThat(response.playerName()).isEqualTo("Deft");
		verify(subscriptionRepository).save(any(MemberFavoritePlayer.class));
	}

	@Test
	@DisplayName("subscribe: LCK·솔랭 둘 다 아니면 400")
	void rejectsNonEligiblePlayer() {
		Member member = member(7L);
		Player nobody = player(9L, "Nobody");

		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(playerRepository.findById(9L)).thenReturn(Optional.of(nobody));
		when(playerRepository.findLckPlayerOption("LCK", 2026, 9L)).thenReturn(List.of());
		when(playerRepository.findSoloRankPlayerOptionsByPlayerIds(Set.of(9L))).thenReturn(List.of());

		assertThatThrownBy(() -> service.subscribe(7L, 9L))
				.isInstanceOf(ResponseStatusException.class);
	}

	private Member member(Long id) {
		Member member = Member.builder().name("용맹한바론").tag("0000").email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private Player player(Long id, String name) {
		Player player = Player.builder().name(name).imageUrl(name.toLowerCase() + ".png").build();
		ReflectionTestUtils.setField(player, "id", id);
		return player;
	}

	private PlayerRepository.LckPlayerOption option(
			Long playerId,
			String playerName,
			Long teamId,
			String teamCode,
			String teamName) {
		PlayerRepository.LckPlayerOption option = org.mockito.Mockito.mock(PlayerRepository.LckPlayerOption.class);
		when(option.getPlayerId()).thenReturn(playerId);
		when(option.getPlayerName()).thenReturn(playerName);
		when(option.getPlayerImageUrl()).thenReturn(playerName.toLowerCase() + ".png");
		when(option.getRole()).thenReturn("mid");
		when(option.getTeamId()).thenReturn(teamId);
		when(option.getTeamCode()).thenReturn(teamCode);
		when(option.getTeamName()).thenReturn(teamName);
		when(option.getTeamImageUrl()).thenReturn(teamCode == null ? null : teamCode.toLowerCase() + ".png");
		return option;
	}
}
