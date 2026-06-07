package com.toy.nar.app.mobile.device.dto;

import com.toy.nar.domain.member.entity.MobileDevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MobileDeviceRegistrationRequest(
		@NotBlank @Size(max = 512) String fcmToken,
		@NotNull MobileDevicePlatform platform) {
}
