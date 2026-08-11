package com.toy.nar.api.auth;

import com.toy.nar.app.auth.AppleUserClient;
import com.toy.nar.app.auth.GoogleUserClient;
import com.toy.nar.app.auth.JwtTokenProvider;
import com.toy.nar.app.auth.KakaoUserClient;
import com.toy.nar.app.auth.NaverUserClient;
import com.toy.nar.app.auth.SocialLoginService;
import com.toy.nar.app.auth.profile.CloudinarySignatureService;
import com.toy.nar.app.auth.profile.ProfileService;
import com.toy.nar.app.mobile.device.MobileDeviceService;
import com.toy.nar.app.mobile.notification.MobileTeamNotificationService;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberRole;
import com.toy.nar.domain.member.entity.RefreshToken;
import com.toy.nar.domain.member.repository.MemberRepository;
import com.toy.nar.domain.member.repository.MemberSocialRepository;
import com.toy.nar.domain.member.repository.RefreshTokenRepository;
import com.toy.nar.domain.participant.repository.PlayerRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 리프레시 회전은 구 토큰을 즉시 지우지 않는다(grace).
 *
 * <p>즉시 삭제하면 동시 리프레시의 늦은 쪽이 findByToken 에서 401 "유효하지 않은 리프레시
 * 토큰"을 받아 클라이언트가 강제 로그아웃된다. 실측 2026-08-11 20:17: 매치 종료 푸시 유입으로
 * 커넥션 풀 대기가 3~5.7초로 벌어지자 레이스 창이 열려 로그아웃 2건 발생. #329 벌크 삭제는
 * 두 요청이 모두 조회에 성공한 경우만 구제하고, 삭제 커밋 뒤에 조회가 도착하는 경우는
 * 그대로 401 이었다.</p>
 */
class AuthControllerRefreshRotationTest {

	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);

	private final AuthController controller = new AuthController(
			jwtTokenProvider,
			mock(KakaoUserClient.class),
			mock(GoogleUserClient.class),
			mock(NaverUserClient.class),
			mock(AppleUserClient.class),
			mock(SocialLoginService.class),
			mock(MemberRepository.class),
			mock(MemberSocialRepository.class),
			refreshTokenRepository,
			mock(TeamRepository.class),
			mock(PlayerRepository.class),
			mock(MobileDeviceService.class),
			mock(MobileTeamNotificationService.class),
			mock(ProfileService.class),
			mock(CloudinarySignatureService.class),
			null);

	@Test
	@DisplayName("회전은 구 토큰을 삭제하지 않고 만료를 단축한다 — 늦은 동시 리프레시도 성공해야 한다")
	void rotationShortensExpiryInsteadOfDelete() {
		RefreshToken stored = storedToken("rt-old", false);
		when(refreshTokenRepository.findByToken("rt-old")).thenReturn(Optional.of(stored));
		when(jwtTokenProvider.createAccessToken(anyLong(), any(Boolean.class), any())).thenReturn("at-new");
		when(jwtTokenProvider.createRefreshToken(anyLong())).thenReturn("rt-new");
		when(jwtTokenProvider.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(14));

		var response = controller.refresh("rt-old");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		verify(refreshTokenRepository).shortenExpiryByToken(eq("rt-old"), any(LocalDateTime.class));
		verify(refreshTokenRepository, never()).deleteByToken("rt-old");
		verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	@DisplayName("만료된 토큰은 벌크 삭제 후 401 — 엔티티 삭제는 동시 요청에서 row count 0 500을 낸다")
	void expiredTokenIsBulkDeletedThen401() {
		RefreshToken stored = storedToken("rt-expired", true);
		when(refreshTokenRepository.findByToken("rt-expired")).thenReturn(Optional.of(stored));

		assertThatThrownBy(() -> controller.refresh("rt-expired"))
				.isInstanceOf(ResponseStatusException.class)
				.hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
		verify(refreshTokenRepository).deleteByToken("rt-expired");
		verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
	}

	@Test
	@DisplayName("모르는 토큰은 401")
	void unknownTokenIs401() {
		when(refreshTokenRepository.findByToken("rt-unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.refresh("rt-unknown"))
				.isInstanceOf(ResponseStatusException.class)
				.hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
	}

	private RefreshToken storedToken(String token, boolean expired) {
		Member member = Member.builder()
				.email("user@example.com")
				.name("유저")
				.tag("0001")
				.build();
		org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 7L);
		return RefreshToken.builder()
				.member(member)
				.token(token)
				.expiresAt(expired ? LocalDateTime.now().minusMinutes(1) : LocalDateTime.now().plusDays(7))
				.build();
	}
}
