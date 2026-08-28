package com.toy.nar.api.auth;

import com.toy.nar.app.auth.AppleUserClient;
import com.toy.nar.app.auth.GoogleUserClient;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.KakaoUserClient;
import com.toy.nar.app.auth.NaverUserClient;
import com.toy.nar.app.auth.SocialLoginService;
import com.toy.nar.app.auth.profile.CloudinarySignatureService;
import com.toy.nar.app.auth.profile.ProfileService;
import com.toy.nar.domain.member.service.FavoriteTeamChangePolicy;
import com.toy.nar.app.mobile.device.MobileDeviceService;
import com.toy.nar.app.mobile.notification.MobileTeamNotificationService;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberSocialRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 탈퇴는 멱등해야 한다.
 * 탈퇴 응답이 늦으면(락 대기) 사용자가 버튼을 연타해 동시 요청이 들어오고, 예전 구현은
 * 이미 지워진 행을 다시 지우다 500(stale state), 이후 재시도는 404 를 냈다(2026-07-29 프로덕션).
 */
class AuthControllerWithdrawTest {

	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final MemberSocialRepository memberSocialRepository = mock(MemberSocialRepository.class);

	private final AuthController controller = new AuthController(
			mock(JwtTokenProvider.class),
			mock(KakaoUserClient.class),
			mock(GoogleUserClient.class),
			mock(NaverUserClient.class),
			mock(AppleUserClient.class),
			mock(SocialLoginService.class),
			memberRepository,
			memberSocialRepository,
			mock(RefreshTokenRepository.class),
			mock(TeamRepository.class),
			mock(PlayerRepository.class),
			mock(MobileDeviceService.class),
			mock(MobileTeamNotificationService.class),
			mock(ProfileService.class),
			mock(FavoriteTeamChangePolicy.class),
			mock(CloudinarySignatureService.class),
				immediateTransactionTemplate());

	@Test
	@DisplayName("탈퇴 시 소셜 연동과 회원을 벌크 삭제하고 204를 반환한다")
	void withdrawDeletesSocialAndMember() {
		when(memberSocialRepository.deleteAllByMemberId(7L)).thenReturn(1);
		when(memberRepository.deleteByMemberId(7L)).thenReturn(1);

		var response = controller.withdraw(7L);

		assertThat(response.getStatusCode().value()).isEqualTo(204);
		verify(memberSocialRepository).deleteAllByMemberId(7L);
		verify(memberRepository).deleteByMemberId(7L);
	}

	@Test
	@DisplayName("이미 탈퇴한 회원으로 다시 호출해도 204다(멱등)")
	void withdrawIsIdempotent() {
		when(memberSocialRepository.deleteAllByMemberId(7L)).thenReturn(0);
		when(memberRepository.deleteByMemberId(7L)).thenReturn(0);

		var response = controller.withdraw(7L);

		assertThat(response.getStatusCode().value()).isEqualTo(204);
	}

	@Test
	@DisplayName("인증 주체가 없으면 401")
	void withdrawWithoutLoginIsUnauthorized() {
		assertThatThrownBy(() -> controller.withdraw(null))
				.isInstanceOf(ResponseStatusException.class)
				.hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
	}

	/** 콜백을 즉시 실행하는 TransactionTemplate — 컨트롤러의 트랜잭션 경계를 프로덕션과 같은 경로로 태운다. */
	private static org.springframework.transaction.support.TransactionTemplate immediateTransactionTemplate() {
		var template = mock(org.springframework.transaction.support.TransactionTemplate.class);
		org.mockito.Mockito.lenient().when(template.execute(org.mockito.ArgumentMatchers.any()))
				.thenAnswer(inv -> inv.getArgument(0,
						org.springframework.transaction.support.TransactionCallback.class).doInTransaction(null));
		return template;
	}

	}
