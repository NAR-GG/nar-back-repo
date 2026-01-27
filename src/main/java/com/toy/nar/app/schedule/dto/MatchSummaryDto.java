package com.toy.nar.app.schedule.dto;

import lombok.Builder;

@Builder
public record MatchSummaryDto(
		String matchId, // 상세 정보 조회를 위한 고유 ID
		String scheduledTime, // "17:00", "19:00"
		String leagueInfo, // "LCK Summer"
		String matchTitle, // "Week 1, Match 5"
		// "inProgress", "completed", "unstarted"
		String matchStatus,
		boolean isSynced,
		TeamResultDto teamA,
		TeamResultDto teamB) {
}
