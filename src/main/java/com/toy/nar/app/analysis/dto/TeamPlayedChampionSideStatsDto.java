package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamPlayedChampionSideStatsDto {
	private List<TeamPlayedChampionPlayerDto> all;
	private List<TeamPlayedChampionPlayerDto> blue;
	private List<TeamPlayedChampionPlayerDto> red;
}
