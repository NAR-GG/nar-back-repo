package com.toy.nar.api.mobile.device;

import com.toy.nar.app.mobile.device.MobileDeviceService;
import com.toy.nar.app.mobile.device.dto.MobileDeviceRegistrationRequest;
import com.toy.nar.app.mobile.device.dto.MobileDeviceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile. 기기 알림", description = "Flutter FCM 기기 토큰 관리 API")
@RestController
@RequestMapping("/api/mobile/me/devices")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MobileDeviceController {

	private final MobileDeviceService deviceService;

	@Operation(summary = "FCM 기기 토큰 등록 또는 갱신")
	@PostMapping
	public ResponseEntity<MobileDeviceResponse> register(
			@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody MobileDeviceRegistrationRequest request) {
		return ResponseEntity.ok(deviceService.register(memberId, request));
	}

	@Operation(summary = "현재 기기 알림 등록 해제")
	@DeleteMapping("/{deviceId}")
	public ResponseEntity<Void> deactivate(
			@AuthenticationPrincipal Long memberId,
			@PathVariable Long deviceId) {
		deviceService.deactivate(memberId, deviceId);
		return ResponseEntity.noContent().build();
	}
}
