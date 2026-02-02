package com.toy.nar.app.schedule.dto;

import lombok.Builder;
import java.util.List;

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
		TeamResultDto teamB,
		String liveStreamUrl, // 진행중 경기 라이브 스트림 URL
		List<SetVodDto> sets // 세트별 VOD 리스트
) {
	/**
	 * 세트별 VOD 정보
	 */
	public record SetVodDto(int setNumber, String vodUrl) {
	}
}
