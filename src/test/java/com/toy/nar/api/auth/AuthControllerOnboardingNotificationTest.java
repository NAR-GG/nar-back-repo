package com.toy.nar.api.auth;

import com.toy.nar.api.auth.dto.OnboardingRequest;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.GoogleUserClient;
import com.toy.nar.app.auth.KakaoUserClient;
import com.toy.nar.app.auth.NaverUserClient;
import com.toy.nar.app.auth.SocialLoginService;
import com.toy.nar.app.auth.profile.CloudinarySignatureService;
import com.toy.nar.app.mobile.device.MobileDeviceService;
import com.toy.nar.app.mobile.notification.MobileTeamNotificationService;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import com.toy.nar.domain.participant.LckTeamCatalog;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerOnboardingNotificationTest {

	@Test
	void onboardingAutomaticallySubscribesFavoriteTeam() {
		JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
		KakaoUserClient kakaoUserClient = mock(KakaoUserClient.class);
		SocialLoginService socialLoginService = mock(SocialLoginService.class);
		MemberRepository memberRepository = mock(MemberRepository.class);
		RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
		TeamRepository teamRepository = mock(TeamRepository.class);
		PlayerRepository playerRepository = mock(PlayerRepository.class);
		MobileDeviceService mobileDeviceService = mock(MobileDeviceService.class);
		MobileTeamNotificationService notificationService = mock(MobileTeamNotificationService.class);
		com.toy.nar.app.auth.profile.ProfileService profileService =
				mock(com.toy.nar.app.auth.profile.ProfileService.class);
		AuthController controller = new AuthController(
				jwtTokenProvider,
				kakaoUserClient,
				mock(GoogleUserClient.class),
				mock(NaverUserClient.class),
				mock(com.toy.nar.app.auth.AppleUserClient.class),
				socialLoginService,
				memberRepository,
				refreshTokenRepository,
				teamRepository,
				playerRepository,
				mobileDeviceService,
				notificationService,
				profileService,
				mock(CloudinarySignatureService.class));
		Member member = Member.builder().name("용맹한바론").tag("0000").email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", 7L);
		Team team = Team.builder().name("T1").code("T1").imageUrl("t1.png").build();
		ReflectionTestUtils.setField(team, "id", 1L);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
		when(teamRepository.findAllByCodeIn(LckTeamCatalog.TEAM_CODES)).thenReturn(List.of(team));

		var response = controller.onboarding(7L, new OnboardingRequest("LCK", 1L, List.of()));

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().favoriteTeamId()).isEqualTo(1L);
		verify(notificationService).ensureDefaultSubscription(member, team);
	}

	@Test
	void onboardingStoresSelectedLeagueIndependentlyFromLckTeamAndPlayers() {
		TestContext context = context();
		Member member = member(7L);
		Team team = team(1L, "T1", "T1");
		when(context.memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(context.teamRepository.findById(1L)).thenReturn(Optional.of(team));
		when(context.teamRepository.findAllByCodeIn(LckTeamCatalog.TEAM_CODES)).thenReturn(List.of(team));

		var response = context.controller.onboarding(
				7L,
				new OnboardingRequest("lpl", 1L, List.of()));

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().favoriteLeagueName()).isEqualTo("LPL");
		assertThat(response.getBody().favoriteTeamId()).isEqualTo(1L);
		verify(context.notificationService).ensureDefaultSubscription(member, team);
	}

	@Test
	void onboardingRejectsNonLckTeamRegardlessOfPreferredLeague() {
		TestContext context = context();
		Member member = member(7L);
		Team lplTeam = team(2L, "Bilibili Gaming", "BLG");
		when(context.memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(context.teamRepository.findById(2L)).thenReturn(Optional.of(lplTeam));
		when(context.teamRepository.findAllByCodeIn(LckTeamCatalog.TEAM_CODES)).thenReturn(List.of());

		assertThatThrownBy(() -> context.controller.onboarding(
				7L,
				new OnboardingRequest("LEC", 2L, List.of())))
				.isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
				.hasMessageContaining("LCK에 속하지 않는 팀");
	}

	@Test
	void onboardingAllowsMoreThanThreePlayers() {
		TestContext context = context();
		Member member = member(7L);
		Team team = team(1L, "T1", "T1");
		List<Player> players = List.of(
				player(10L, "Player1"),
				player(11L, "Player2"),
				player(12L, "Player3"),
				player(13L, "Player4"));
		when(context.memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(context.teamRepository.findById(1L)).thenReturn(Optional.of(team));
		when(context.teamRepository.findAllByCodeIn(LckTeamCatalog.TEAM_CODES)).thenReturn(List.of(team));
		when(context.playerRepository.findAllById(java.util.Set.of(10L, 11L, 12L, 13L)))
				.thenReturn(players);
		when(context.playerRepository.findOnboardingPlayers("LCK", 2026, 1L))
				.thenReturn(players);

		var response = context.controller.onboarding(
				7L,
				new OnboardingRequest("LCK", 1L, List.of(10L, 11L, 12L, 13L)));

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().favoritePlayerIds())
				.containsExactlyInAnyOrder(10L, 11L, 12L, 13L);
	}

	@Test
	void onboardingRejectsPlayerOutsideSelectedLckTeam() {
		TestContext context = context();
		Member member = member(7L);
		Team team = team(1L, "T1", "T1");
		Player player = player(10L, "Faker");
		when(context.memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(context.teamRepository.findById(1L)).thenReturn(Optional.of(team));
		when(context.teamRepository.findAllByCodeIn(LckTeamCatalog.TEAM_CODES)).thenReturn(List.of(team));
		when(context.playerRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of(player));
		when(context.playerRepository.findOnboardingPlayers("LCK", 2026, 1L)).thenReturn(List.of());

		assertThatThrownBy(() -> context.controller.onboarding(
				7L,
				new OnboardingRequest("LPL", 1L, List.of(10L))))
				.isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
				.hasMessageContaining("선택한 LCK 팀에 속하지 않는 선수");
	}

	@Test
	void onboardingTeamsAndPlayersAlwaysUseLck() {
		TestContext context = context();
		Team t1 = team(1L, "T1", "T1");
		when(context.teamRepository.findAllByCodeIn(LckTeamCatalog.TEAM_CODES)).thenReturn(List.of(t1));
		when(context.playerRepository.findOnboardingPlayers("LCK", 2026, 1L)).thenReturn(List.of());

		var teams = context.controller.getOnboardingTeams(2026);
		context.controller.getOnboardingPlayers(2026, 1L);

		assertThat(teams.getBody()).isNotNull();
		assertThat(teams.getBody()).extracting("code").containsExactly("T1");
		verify(context.playerRepository).findOnboardingPlayers("LCK", 2026, 1L);
	}

	private TestContext context() {
		JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
		KakaoUserClient kakaoUserClient = mock(KakaoUserClient.class);
		SocialLoginService socialLoginService = mock(SocialLoginService.class);
		MemberRepository memberRepository = mock(MemberRepository.class);
		RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
		TeamRepository teamRepository = mock(TeamRepository.class);
		PlayerRepository playerRepository = mock(PlayerRepository.class);
		MobileDeviceService mobileDeviceService = mock(MobileDeviceService.class);
		MobileTeamNotificationService notificationService = mock(MobileTeamNotificationService.class);
		com.toy.nar.app.auth.profile.ProfileService profileService =
				mock(com.toy.nar.app.auth.profile.ProfileService.class);
		AuthController controller = new AuthController(
				jwtTokenProvider,
				kakaoUserClient,
				mock(GoogleUserClient.class),
				mock(NaverUserClient.class),
				mock(com.toy.nar.app.auth.AppleUserClient.class),
				socialLoginService,
				memberRepository,
				refreshTokenRepository,
				teamRepository,
				playerRepository,
				mobileDeviceService,
				notificationService,
				profileService,
				mock(CloudinarySignatureService.class));
		return new TestContext(
				controller,
				memberRepository,
				teamRepository,
				playerRepository,
				notificationService);
	}

	private Member member(Long id) {
		Member member = Member.builder().name("용맹한바론").tag("0000").email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private Team team(Long id, String name, String code) {
		Team team = Team.builder().name(name).code(code).imageUrl(code + ".png").build();
		ReflectionTestUtils.setField(team, "id", id);
		return team;
	}

	private Player player(Long id, String name) {
		Player player = Player.builder().name(name).imageUrl(name + ".png").build();
		ReflectionTestUtils.setField(player, "id", id);
		return player;
	}

	private record TestContext(
			AuthController controller,
			MemberRepository memberRepository,
			TeamRepository teamRepository,
			PlayerRepository playerRepository,
			MobileTeamNotificationService notificationService) {
	}
}
