package com.toy.nar.app.mobile.solorank.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "구독 선수의 솔로 랭크 상태 — 지금 하는 중인 선수와 최근 24시간 안에 끝낸 선수")
public record SoloRankStatusResponse(
		@Schema(description = "지금 솔랭 중. 최근 시작순")
		List<LivePlayer> live,
		@Schema(description = "최근 24시간 안에 끝난 게임, 선수당 마지막 1판. 최근 종료순. 노출 기간·인원 상한은 앱이 정한다")
		List<FinishedPlayer> finished) {

	public record LivePlayer(
			Long playerId,
			String playerName,
			String playerImageUrl,
			String teamCode,
			String championName,
			String championImageUrl,
			@Schema(description = "게임 시작 시각. 로딩 화면에 감지돼 실제 시작을 아직 모르면 감지 시각", example = "2026-09-25T21:03:12+09:00")
			OffsetDateTime startedAt) {
	}

	public record FinishedPlayer(
			Long playerId,
			String playerName,
			String playerImageUrl,
			String teamCode,
			String championName,
			String championImageUrl,
			Boolean win,
			Integer kills,
			Integer deaths,
			Integer assists,
			@Schema(description = "경기 길이(초)", example = "1920")
			Integer durationSeconds,
			@Schema(example = "2026-09-25T21:35:12+09:00")
			OffsetDateTime endedAt) {
	}
}
