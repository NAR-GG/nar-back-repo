package com.toy.nar.api.mobile.notification;

import com.toy.nar.app.mobile.notification.MobileTeamNotificationService;
import com.toy.nar.app.mobile.notification.dto.TeamNotificationSubscribeRequest;
import com.toy.nar.app.mobile.notification.dto.TeamNotificationSubscriptionResponse;
import com.toy.nar.app.mobile.notification.dto.TeamNotificationUpdateRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Mobile. 팀 알림 설정", description = "마이페이지 팀별 알림 구독 설정 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/mobile/me/notification-subscriptions")
@RequiredArgsConstructor
public class MobileTeamNotificationController {

	private final MobileTeamNotificationService notificationService;

	@Operation(summary = "내 팀 알림 구독 목록 조회")
	@GetMapping
	public ResponseEntity<List<TeamNotificationSubscriptionResponse>> getSubscriptions(
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(notificationService.getSubscriptions(memberId));
	}

	@Operation(summary = "구독 가능한 LCK 팀 목록 조회")
	@GetMapping("/available-teams")
	public ResponseEntity<List<TeamNotificationSubscriptionResponse>> getAvailableTeams(
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(notificationService.getAvailableTeams(memberId));
	}

	@Operation(summary = "팀 알림 구독 추가")
	@PostMapping
	public ResponseEntity<TeamNotificationSubscriptionResponse> subscribe(
			@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody TeamNotificationSubscribeRequest request) {
		return ResponseEntity.ok(notificationService.subscribe(memberId, request.teamId()));
	}

	@Operation(summary = "팀별 알림 설정 변경")
	@PutMapping("/{teamId}")
	public ResponseEntity<TeamNotificationSubscriptionResponse> update(
			@AuthenticationPrincipal Long memberId,
			@PathVariable Long teamId,
			@Valid @RequestBody TeamNotificationUpdateRequest request) {
		return ResponseEntity.ok(notificationService.update(memberId, teamId, request));
	}

	@Operation(summary = "팀 알림 구독 삭제")
	@DeleteMapping("/{teamId}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal Long memberId,
			@PathVariable Long teamId) {
		notificationService.delete(memberId, teamId);
		return ResponseEntity.noContent().build();
	}
}
