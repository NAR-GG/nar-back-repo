package com.toy.nar.app.mobile.solorank.dto;

import com.toy.nar.app.mobile.subscription.dto.PlayerSubscriptionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "월즈 솔랭 — 월즈 부트캠프 계정이 등록된 선수 전체의 솔로 랭크 상태")
public record WorldsSoloRankResponse(
		@Schema(description = "명단 전체(지금 안 하는 선수 포함). 내 구독 여부·알림 토글이 실린다. 역할·이름순")
		List<PlayerSubscriptionResponse> players,
		@Schema(description = "지금 솔랭 중. 최근 시작순")
		List<SoloRankStatusResponse.LivePlayer> live,
		@Schema(description = "최근 24시간 안에 끝난 게임, 선수당 마지막 1판. 최근 종료순")
		List<SoloRankStatusResponse.FinishedPlayer> finished) {
}
