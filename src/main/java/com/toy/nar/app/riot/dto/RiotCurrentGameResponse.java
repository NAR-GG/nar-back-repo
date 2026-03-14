package com.toy.nar.app.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotCurrentGameResponse(
		Long gameId,
		Integer gameQueueConfigId) {
}
