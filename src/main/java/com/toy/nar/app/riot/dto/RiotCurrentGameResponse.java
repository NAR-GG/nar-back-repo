package com.toy.nar.app.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotCurrentGameResponse(
		Long gameId,
		Integer gameQueueConfigId,
		List<RiotCurrentGameParticipantResponse> participants) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RiotCurrentGameParticipantResponse(
			String puuid,
			Integer championId,
			String riotId) {
	}
}
