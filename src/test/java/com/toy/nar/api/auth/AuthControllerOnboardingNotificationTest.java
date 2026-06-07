package com.toy.nar.api.auth;

import com.toy.nar.api.auth.dto.OnboardingRequest;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.KakaoUserClient;
import com.toy.nar.app.auth.SocialLoginService;
import com.toy.nar.app.mobile.device.MobileDeviceService;
import com.toy.nar.app.mobile.notification.MobileTeamNotificationService;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
		AuthController controller = new AuthController(
				jwtTokenProvider,
				kakaoUserClient,
				socialLoginService,
				memberRepository,
				refreshTokenRepository,
				teamRepository,
				playerRepository,
				mobileDeviceService,
				notificationService);
		Member member = Member.builder().nickname("용맹한바론").email("test@example.com").build();
		ReflectionTestUtils.setField(member, "id", 7L);
		Team team = Team.builder().name("T1").code("T1").imageUrl("t1.png").build();
		ReflectionTestUtils.setField(team, "id", 1L);
		when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
		when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
		when(teamRepository.findAllByCodeIn(anyCollection())).thenReturn(List.of(team));

		var response = controller.onboarding(7L, new OnboardingRequest("LCK", 1L, List.of()));

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().favoriteTeamId()).isEqualTo(1L);
		verify(notificationService).ensureDefaultSubscription(member, team);
	}

	private static <T> java.util.Collection<T> anyCollection() {
		return org.mockito.ArgumentMatchers.anyCollection();
	}
}
