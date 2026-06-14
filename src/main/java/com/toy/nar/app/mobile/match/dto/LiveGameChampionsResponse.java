package com.toy.nar.app.mobile.match.dto;

import java.util.List;

/** 라이브 경기 챔피언 픽/밴 화면용 응답. */
public record LiveGameChampionsResponse(
		String gameId,
		TeamChampions blueTeam,
		TeamChampions redTeam) {

	public record TeamChampions(
			String teamName,
			List<Pick> picks,
			List<Ban> bans) {
	}

	public record Pick(
			String position,
			String championName,
			String championImageUrl,
			String playerName) {
	}

	public record Ban(
			String championName,
			String championImageUrl) {
	}
}
