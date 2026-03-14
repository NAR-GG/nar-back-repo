package com.toy.nar.app.riot.dto;

public record RiotMatchResponse(
		Metadata metadata,
		Info info) {

	public record Metadata(
			String matchId) {
	}

	public record Info(
			Integer queueId) {
	}
}
