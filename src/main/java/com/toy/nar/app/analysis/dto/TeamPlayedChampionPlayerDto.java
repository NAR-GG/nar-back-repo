package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamPlayedChampionPlayerDto {
	private Long playerId;
	private String playerName;
	private String playerImageUrl;
	private String position;
	private List<TeamPlayedChampionStatDto> champions;
}
