package com.toy.nar.app.data.ingestion.dto;

import com.toy.nar.app.data.ingestion.DataIngestionFacade;
import com.toy.nar.domain.game.entity.League;

public record LeagueIdentifier(String name, int year, String split, boolean isPlayoffs) {
	public static LeagueIdentifier fromDto(GameDataCsvDto dto) {
		return new LeagueIdentifier(dto.getLeague(), dto.getYear(), dto.getSplit(), dto.getPlayoffs() == 1);
	}
	public static LeagueIdentifier fromEntity(League league) {
		return new LeagueIdentifier(league.getLeagueName(), league.getSeasonYear(), league.getSeasonSplit(), league.getIsPlayoffs());
	}
}
