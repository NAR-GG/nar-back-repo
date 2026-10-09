package com.toy.nar.app.mobile.solorank.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "솔랭 응원 전송. 앱이 짧은 시간의 탭을 모아 한 번에 보낸다")
public record SoloRankCheerRequest(
		@Schema(description = "이번에 보내는 응원 수. 1~30", example = "5")
		int count) {
}
