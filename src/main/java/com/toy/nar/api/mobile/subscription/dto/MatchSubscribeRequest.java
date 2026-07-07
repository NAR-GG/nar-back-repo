package com.toy.nar.api.mobile.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "경기 예약 알림 구독 요청")
public record MatchSubscribeRequest(
		@NotBlank
		@Schema(description = "구독할 경기 ID", example = "113990000000000001")
		String matchId) {
}
