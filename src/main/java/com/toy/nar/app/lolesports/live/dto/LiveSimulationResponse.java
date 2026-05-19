package com.toy.nar.app.lolesports.live.dto;

import java.time.LocalDateTime;

public record LiveSimulationResponse(
		String gameId,
		LocalDateTime firstStartingTimeUtc,
		Integer ticksRequested,
		Integer stepSeconds,
		Integer processedFrames,
		Integer emptyResponses,
		Integer failures,
		LocalDateTime firstFrameTimestampUtc,
		LocalDateTime lastFrameTimestampUtc) {
}
