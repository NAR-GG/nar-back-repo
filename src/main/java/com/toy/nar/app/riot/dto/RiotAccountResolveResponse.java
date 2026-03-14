package com.toy.nar.app.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotAccountResolveResponse(
		String puuid,
		String gameName,
		String tagLine) {
}
