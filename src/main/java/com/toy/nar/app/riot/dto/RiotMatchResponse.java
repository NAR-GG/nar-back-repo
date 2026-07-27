package com.toy.nar.app.riot.dto;

import java.util.List;

public record RiotMatchResponse(
		Metadata metadata,
		Info info) {

	public record Metadata(
			String matchId) {
	}

	public record Info(
			Integer queueId,
			Long gameEndTimestamp,
			List<Participant> participants) {
	}

	public record Participant(
			String puuid,
			Integer championId,
			Boolean win,
			Integer kills,
			Integer deaths,
			Integer assists) {
	}
}
