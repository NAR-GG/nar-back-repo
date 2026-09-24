package com.toy.nar.app.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * spectator-v5 현재 게임.
 *
 * @param gameStartTime 실제 시작 시각(epoch 밀리초). 로딩 화면 동안은 0 으로 내려온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotCurrentGameResponse(
		Long gameId,
		Integer gameQueueConfigId,
		List<RiotCurrentGameParticipantResponse> participants,
		Long gameStartTime) {

	public RiotCurrentGameResponse(
			Long gameId,
			Integer gameQueueConfigId,
			List<RiotCurrentGameParticipantResponse> participants) {
		this(gameId, gameQueueConfigId, participants, null);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RiotCurrentGameParticipantResponse(
			String puuid,
			Integer championId,
			String riotId) {
	}
}
