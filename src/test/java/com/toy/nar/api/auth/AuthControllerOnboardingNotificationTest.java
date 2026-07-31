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
import com.toy.nar.domain.member.repository.MemberSocialRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
				mock(MemberSocialRepository.class),
				refreshTokenRepository,
				teamRepository,
				playerRepository,
				mobileDeviceService,
				notificationService,
				profileService,
				mock(CloudinarySignatureService.class),
				immediateTransactionTemplate());
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
		var lckOpts = lckOptions(players);
		when(context.playerRepository.findLckPlayerOptions(eq("LCK"), eq(2026), eq(1L), isNull(), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(lckOpts));

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
		var lckOpts = lckOptions(List.of());
		when(context.playerRepository.findLckPlayerOptions(eq("LCK"), eq(2026), eq(1L), isNull(), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(lckOpts));

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
		var lckOpts = lckOptions(List.of());
		when(context.playerRepository.findLckPlayerOptions(eq("LCK"), eq(2026), eq(1L), isNull(), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(lckOpts));

		var teams = context.controller.getOnboardingTeams(2026);
		context.controller.getOnboardingPlayers(2026, 1L);

		assertThat(teams.getBody()).isNotNull();
		assertThat(teams.getBody()).extracting("code").containsExactly("T1");
		verify(context.playerRepository).findLckPlayerOptions(eq("LCK"), eq(2026), eq(1L), isNull(), any());
	}

	/**
	 * 온보딩 연타로 동시 요청이 겹치면 늦은 쪽이 즐겨찾기 유니크 제약(중복키)에 걸린다.
	 * 실측 2026-07-31 새벽 29건 — 앞선 요청이 이미 완료된 상태이므로 500 이 아니라
	 * 현재 상태로 200 을 돌려줘야 한다(앱이 온보딩 실패로 오인하지 않게).
	 */
	@Test
	void onboardingAbsorbsConcurrentDuplicateAsSuccess() {
		TestContext context = context();
		Member member = member(7L);
		Team team = team(1L, "T1", "T1");
		Player faker = player(10L, "Faker");
		member.completeOnboarding("LCK", team, List.of(faker)); // 앞선 요청이 이미 완료한 상태
		when(context.memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(context.teamRepository.findById(1L)).thenReturn(Optional.of(team));
		when(context.teamRepository.findAllByCodeIn(LckTeamCatalog.TEAM_CODES)).thenReturn(List.of(team));
		when(context.playerRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of(faker));
		var lckOpts = lckOptions(List.of(faker));
		when(context.playerRepository.findLckPlayerOptions(eq("LCK"), eq(2026), eq(1L), isNull(), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(lckOpts));
		// 트랜잭션 커밋 시점의 중복키를 재현: 첫 execute 는 예외, 흡수 후 재조회 execute 는 정상 실행
		var template = mock(org.springframework.transaction.support.TransactionTemplate.class);
		when(template.execute(any()))
				.thenThrow(new org.springframework.dao.DataIntegrityViolationException(
						"Duplicate entry '7-10' for key 'uq_member_favorite_player'"))
				.thenAnswer(inv -> inv.getArgument(0,
						org.springframework.transaction.support.TransactionCallback.class).doInTransaction(null));
		ReflectionTestUtils.setField(context.controller, "transactionTemplate", template);

		var response = context.controller.onboarding(7L, new OnboardingRequest("LCK", 1L, List.of(10L)));

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().favoritePlayerIds()).containsExactly(10L);
	}

	// 탈퇴 관련 테스트는 AuthControllerWithdrawTest 로 옮겼다(멱등 삭제로 동작이 바뀜).

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
		MemberSocialRepository memberSocialRepository = mock(MemberSocialRepository.class);
		AuthController controller = new AuthController(
				jwtTokenProvider,
				kakaoUserClient,
				mock(GoogleUserClient.class),
				mock(NaverUserClient.class),
				mock(com.toy.nar.app.auth.AppleUserClient.class),
				socialLoginService,
				memberRepository,
				memberSocialRepository,
				refreshTokenRepository,
				teamRepository,
				playerRepository,
				mobileDeviceService,
				notificationService,
				profileService,
				mock(CloudinarySignatureService.class),
				immediateTransactionTemplate());
		return new TestContext(
				memberSocialRepository,
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
			MemberSocialRepository memberSocialRepository,
			AuthController controller,
			MemberRepository memberRepository,
			TeamRepository teamRepository,
			PlayerRepository playerRepository,
			MobileTeamNotificationService notificationService) {
	}

	/** 콜백을 즉시 실행하는 TransactionTemplate — 컨트롤러의 트랜잭션 경계를 프로덕션과 같은 경로로 태운다. */
	private static org.springframework.transaction.support.TransactionTemplate immediateTransactionTemplate() {
		var template = mock(org.springframework.transaction.support.TransactionTemplate.class);
		org.mockito.Mockito.lenient().when(template.execute(org.mockito.ArgumentMatchers.any()))
				.thenAnswer(inv -> inv.getArgument(0,
						org.springframework.transaction.support.TransactionCallback.class).doInTransaction(null));
		return template;
	}

	/** Player 목록을 LckPlayerOption 프로젝션 목록으로 바꾼다(선수 검증 스텁용). */
	private static List<com.toy.nar.domain.participant.repository.PlayerRepository.LckPlayerOption> lckOptions(
			List<Player> players) {
		return players.stream().map(pl -> {
			var opt = mock(com.toy.nar.domain.participant.repository.PlayerRepository.LckPlayerOption.class);
			org.mockito.Mockito.lenient().when(opt.getPlayerId()).thenReturn(pl.getId());
			org.mockito.Mockito.lenient().when(opt.getPlayerName()).thenReturn(pl.getName());
			return (com.toy.nar.domain.participant.repository.PlayerRepository.LckPlayerOption) opt;
		}).toList();
	}
}
