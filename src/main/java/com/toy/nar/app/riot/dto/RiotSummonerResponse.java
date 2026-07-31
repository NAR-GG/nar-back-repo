package com.toy.nar.app.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotSummonerResponse(
		String id,
		String puuid,
		Integer summonerLevel,
		Long revisionDate) {
}
