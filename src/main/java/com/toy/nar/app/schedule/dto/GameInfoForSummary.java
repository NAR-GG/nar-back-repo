package com.toy.nar.app.schedule.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GameInfoForSummary(
	Long gameId,
	LocalDateTime scheduledGameStartTime,
	String leagueName,
	String seasonSplit,
	List<ParticipantInfo> participants
) {
	public record ParticipantInfo(String teamName, boolean isWin) {}
}
