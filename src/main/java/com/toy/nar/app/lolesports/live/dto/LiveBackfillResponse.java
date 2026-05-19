package com.toy.nar.app.lolesports.live.dto;

import java.time.LocalDateTime;

public record LiveBackfillResponse(
		String gameId,
		String startingTimeSource,
		LocalDateTime baseStartingTimeUtc,
		LocalDateTime resolvedStartingTimeUtc,
		Integer probeAttempts,
		Integer minutesRequested,
		Integer snapshotsWritten,
		Integer emptyResponses,
		Integer failures,
		String stopReason,
		LocalDateTime firstFrameTimestampUtc,
		LocalDateTime lastFrameTimestampUtc) {
}
