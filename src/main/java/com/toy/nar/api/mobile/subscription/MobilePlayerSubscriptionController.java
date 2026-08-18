package com.toy.nar.api.mobile.subscription;

import com.toy.nar.app.mobile.subscription.MobilePlayerSubscriptionService;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionPageResponse;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionRequest;
import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.toy.nar.api.mobile.subscription.dto.PlayerSubscriptionToggleRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Mobile. 선수 구독", description = "마이페이지 LCK 선수 구독 관리 API")
@RestController
@RequestMapping("/api/mobile/me/player-subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MobilePlayerSubscriptionController {

	private final MobilePlayerSubscriptionService subscriptionService;

	@Operation(summary = "내 구독 선수 목록 조회")
	@GetMapping
	public ResponseEntity<List<PlayerSubscriptionResponse>> getSubscriptions(
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(subscriptionService.getSubscriptions(memberId));
	}

	@Operation(summary = "구독 가능한 2026 LCK 선수 검색")
	@GetMapping("/available-players")
	public ResponseEntity<PlayerSubscriptionPageResponse> getAvailablePlayers(
			@AuthenticationPrincipal Long memberId,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) Long teamId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(subscriptionService.getAvailablePlayers(
				memberId,
				query,
				teamId,
				page,
				size));
	}

	@Operation(summary = "선수 구독 추가")
	@PostMapping
	public ResponseEntity<PlayerSubscriptionResponse> subscribe(
			@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody PlayerSubscriptionRequest request) {
		return ResponseEntity.ok(subscriptionService.subscribe(memberId, request.playerId()));
	}

	@Operation(summary = "선수 알림 토글 변경",
			description = "구독은 유지한 채 솔랭 시작/종료 알림을 켜고 끈다. 보내지 않은 필드는 기존 값을 유지한다.")
	@PutMapping("/{playerId}")
	public ResponseEntity<Void> updateToggles(
			@AuthenticationPrincipal Long memberId,
			@PathVariable Long playerId,
			@RequestBody PlayerSubscriptionToggleRequest request) {
		subscriptionService.updateToggles(memberId, playerId, request.startEnabled(), request.endEnabled());
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "선수 구독 해제")
	@DeleteMapping("/{playerId}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal Long memberId,
			@PathVariable Long playerId) {
		subscriptionService.delete(memberId, playerId);
		return ResponseEntity.noContent().build();
	}
}
