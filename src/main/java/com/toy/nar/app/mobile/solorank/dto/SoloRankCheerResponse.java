package com.toy.nar.app.mobile.solorank.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SoloRankCheerResponse(
		@Schema(description = "이 판의 전체 응원 수")
		long cheerTotal,
		@Schema(description = "이 판에 내가 보낸 응원 수")
		long cheerMine) {
}
