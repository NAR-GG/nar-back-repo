package com.toy.nar.app.mobile.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/** 알림 잠자기 설정. 시각은 "HH:mm" 문자열로 주고받는다. */
public record QuietHoursResponse(
		boolean enabled,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime) {
}
