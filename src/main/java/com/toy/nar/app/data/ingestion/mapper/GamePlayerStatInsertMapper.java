package com.toy.nar.app.data.ingestion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.toy.nar.app.data.ingestion.dto.GamePlayerStatInsertRow;
import com.toy.nar.domain.participant.entity.GamePlayerStat;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GamePlayerStatInsertMapper {

	@Mapping(target = "gameParticipantId", source = "gameParticipantId")
	@Mapping(target = "isFirstBloodKill", source = "stat.firstBloodKill")
	@Mapping(target = "isFirstBloodAssist", source = "stat.firstBloodAssist")
	@Mapping(target = "isFirstBloodVictim", source = "stat.firstBloodVictim")
	GamePlayerStatInsertRow toRow(Long gameParticipantId, GamePlayerStat stat);
}
