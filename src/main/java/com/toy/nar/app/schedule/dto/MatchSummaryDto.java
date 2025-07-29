package com.toy.nar.app.schedule.dto;

public record MatchSummaryDto(
	String matchId,       // 상세 정보 조회를 위한 고유 ID
	String scheduledTime, // "17:00", "19:00"
	String leagueInfo,    // "LCK Summer"
	TeamResultDto teamA,
	TeamResultDto teamB
) {}

