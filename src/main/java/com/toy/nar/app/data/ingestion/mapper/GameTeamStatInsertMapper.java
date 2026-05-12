package com.toy.nar.app.data.ingestion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.toy.nar.app.data.ingestion.dto.GameTeamStatInsertRow;
import com.toy.nar.domain.participant.entity.GameTeamStat;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GameTeamStatInsertMapper {

	@Mapping(target = "gameId", source = "gameId")
	@Mapping(target = "teamId", source = "teamId")
	@Mapping(target = "isFirstBlood", source = "stat.firstBlood")
	@Mapping(target = "isFirstDragon", source = "stat.firstDragon")
	@Mapping(target = "isFirstHerald", source = "stat.firstHerald")
	@Mapping(target = "isFirstBaron", source = "stat.firstBaron")
	@Mapping(target = "isFirstTower", source = "stat.firstTower")
	@Mapping(target = "isFirstMidTower", source = "stat.firstMidTower")
	@Mapping(target = "isFirstTopTower", source = "stat.firstTopTower")
	@Mapping(target = "isFirstBotTower", source = "stat.firstBotTower")
	@Mapping(target = "isFirstToThreeTowers", source = "stat.firstToThreeTowers")
	GameTeamStatInsertRow toRow(Long gameId, Long teamId, GameTeamStat stat);
}
