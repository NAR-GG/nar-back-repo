package com.toy.nar.app.lolesports.live.dto;

import java.time.LocalDateTime;

public record LiveGameSummaryResponse(
		String gameId,
		String matchId,
		String leagueName,
		String blueTeamName,
		String redTeamName,
		LocalDateTime frameTimestampUtc) {
}

