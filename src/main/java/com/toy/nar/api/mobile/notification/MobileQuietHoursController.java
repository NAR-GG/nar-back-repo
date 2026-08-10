package com.toy.nar.api.mobile.notification;

import com.toy.nar.app.mobile.notification.MobileQuietHoursService;
import com.toy.nar.app.mobile.notification.dto.QuietHoursResponse;
import com.toy.nar.app.mobile.notification.dto.QuietHoursUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 알림 잠자기", description = "정한 시간대에 푸시를 소리 없이 받는 설정")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/mobile/me/quiet-hours")
@RequiredArgsConstructor
public class MobileQuietHoursController {

	private final MobileQuietHoursService quietHoursService;

	@Operation(summary = "내 알림 잠자기 설정 조회")
	@GetMapping
	public ResponseEntity<QuietHoursResponse> get(@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(quietHoursService.get(memberId));
	}

	@Operation(summary = "내 알림 잠자기 설정 변경")
	@PutMapping
	public ResponseEntity<QuietHoursResponse> update(
			@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody QuietHoursUpdateRequest request) {
		return ResponseEntity.ok(quietHoursService.update(memberId, request));
	}
}
