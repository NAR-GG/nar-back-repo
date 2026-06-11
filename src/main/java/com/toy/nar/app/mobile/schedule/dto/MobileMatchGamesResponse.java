package com.toy.nar.app.mobile.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "매치 세트(게임) 목록 응답")
public record MobileMatchGamesResponse(
		String matchId,
		List<MobileScheduleListResponse.MobileGameSummary> games) {
}
