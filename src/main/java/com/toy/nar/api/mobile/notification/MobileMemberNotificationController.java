package com.toy.nar.api.mobile.notification;

import com.toy.nar.app.mobile.notification.MemberNotificationService;
import com.toy.nar.app.mobile.notification.dto.MemberNotificationListResponse;
import com.toy.nar.domain.member.entity.MemberNotificationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 마이구독 알림", description = "마이구독 알림 리스트 전체 페이지 API")
@RestController
@RequestMapping("/api/mobile/me/notifications")
@RequiredArgsConstructor
public class MobileMemberNotificationController {

	private final MemberNotificationService notificationService;

	@Operation(summary = "알림 리스트 조회",
			description = "로그인한 회원이 받은 알림을 최신순으로 조회한다. type 으로 필터(미지정 시 전체).")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping
	public ResponseEntity<MemberNotificationListResponse> getNotifications(
			@AuthenticationPrincipal Long memberId,
			@RequestParam(required = false) MemberNotificationType type,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(notificationService.getNotifications(memberId, type, page, size));
	}

	@Operation(summary = "알림 전체 읽음 처리", description = "미읽음 알림을 모두 읽음으로 표시하고 처리한 건수를 반환한다.")
	@SecurityRequirement(name = "bearerAuth")
	@PostMapping("/read")
	public ResponseEntity<Integer> markAllRead(@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(notificationService.markAllRead(memberId));
	}

	@Operation(summary = "알림 단건 읽음 처리")
	@SecurityRequirement(name = "bearerAuth")
	@PostMapping("/{notificationId}/read")
	public ResponseEntity<Void> markRead(
			@AuthenticationPrincipal Long memberId,
			@PathVariable Long notificationId) {
		notificationService.markRead(memberId, notificationId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "알림 전체 삭제", description = "회원의 알림을 모두 삭제하고 삭제 건수를 반환한다.")
	@SecurityRequirement(name = "bearerAuth")
	@DeleteMapping
	public ResponseEntity<Integer> deleteAll(@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(notificationService.deleteAll(memberId));
	}

	@Operation(summary = "알림 단건 삭제")
	@SecurityRequirement(name = "bearerAuth")
	@DeleteMapping("/{notificationId}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal Long memberId,
			@PathVariable Long notificationId) {
		notificationService.delete(memberId, notificationId);
		return ResponseEntity.noContent().build();
	}
}
