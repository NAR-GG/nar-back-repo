package com.toy.nar.api.mobile.liveactivity;

import com.toy.nar.app.mobile.liveactivity.LiveActivityTokenService;
import com.toy.nar.app.mobile.liveactivity.dto.LiveActivityTokenRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. Live Activity", description = "iOS 잠금화면 실시간 경기 카드의 ActivityKit 푸시 토큰 관리 API")
@RestController
@RequestMapping("/api/mobile/me/live-activities")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MobileLiveActivityController {

	private final LiveActivityTokenService tokenService;

	@Operation(
			summary = "Live Activity 푸시 토큰 등록/갱신",
			description = "카드를 띄울 때 ActivityKit 이 준 토큰을 올린다. 토큰은 액티비티 단위라 "
					+ "카드마다 새로 발급되고 수명 중 갱신될 수 있으므로, 받을 때마다 그대로 호출하면 된다. "
					+ "FCM 기기 토큰(/api/mobile/me/devices)과는 다른 값이다.")
	@PostMapping
	public ResponseEntity<Void> register(
			@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody LiveActivityTokenRequest request) {
		tokenService.register(memberId, request);
		return ResponseEntity.noContent().build();
	}

	@Operation(
			summary = "Live Activity 푸시 토큰 해제",
			description = "사용자가 카드를 직접 내렸을 때 호출한다. 매치 종료 시에는 서버가 알아서 정리한다.")
	@DeleteMapping
	public ResponseEntity<Void> unregister(
			@AuthenticationPrincipal Long memberId,
			@Parameter(description = "등록할 때 올린 ActivityKit 푸시 토큰")
			@RequestParam String pushToken) {
		tokenService.unregister(memberId, pushToken);
		return ResponseEntity.noContent().build();
	}
}
