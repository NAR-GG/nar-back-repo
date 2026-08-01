package com.toy.nar.app.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** league-v4 랭크 엔트리. 큐별로 1건씩 배열로 온다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotLeagueEntryResponse(
		String queueType,
		String tier,
		String rank,
		Integer leaguePoints,
		Integer wins,
		Integer losses) {
}
