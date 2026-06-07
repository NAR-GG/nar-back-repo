package com.toy.nar.app.mobile.device.dto;

import com.toy.nar.domain.member.entity.MemberDevice;
import com.toy.nar.domain.member.entity.MobileDevicePlatform;

public record MobileDeviceResponse(
		Long deviceId,
		MobileDevicePlatform platform,
		boolean active) {

	public static MobileDeviceResponse from(MemberDevice device) {
		return new MobileDeviceResponse(
				device.getId(),
				device.getPlatform(),
				device.isActive());
	}
}
