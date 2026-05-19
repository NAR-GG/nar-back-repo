package com.toy.nar.app.lolesports.live.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LiveGameState(
		String gameId,
		String matchId,
		String leagueName,
		String blueTeamName,
		String redTeamName,
		LocalDateTime minuteBucketUtc,
		LocalDateTime frameTimestampUtc,
		List<LiveParticipantState> participants,
		List<LiveObjectEventResponse> objectTimeline) {
}
