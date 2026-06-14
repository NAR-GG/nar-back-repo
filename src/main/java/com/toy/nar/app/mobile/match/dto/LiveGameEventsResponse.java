package com.toy.nar.app.mobile.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 라이브 경기 이벤트 타임라인 응답(최신순).
 * KILL/DRAGON/BARON/TOWER/INHIBITOR 이벤트가 한 배열에 섞여 내려가며,
 * type 에 따라 채워지는 필드가 다르므로 NULL 필드는 응답에서 생략한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveGameEventsResponse(
		String gameId,
		String blueTeamName,
		String blueTeamImageUrl,
		String redTeamName,
		String redTeamImageUrl,
		List<Event> events) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Event(
			String type,
			String gameTime,
			Integer gameTimeSeconds,
			// KILL 전용
			Participant killer,
			Participant victim,
			Integer teamKillCount,
			// DRAGON/BARON/TOWER/INHIBITOR 전용
			String subType,
			String teamSide,
			String teamName,
			Integer count) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Participant(
			String playerName,
			String championName,
			String championImageUrl,
			String teamSide) {
	}
}
