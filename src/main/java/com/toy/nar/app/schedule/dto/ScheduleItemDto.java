package com.toy.nar.app.schedule.dto;

import java.time.LocalDateTime;

public record ScheduleItemDto(
	Long gameId,
	String leagueName,
	String seasonSplit,
	LocalDateTime scheduledGameStartTime,
	String teamName,
	boolean isWin
) {
}