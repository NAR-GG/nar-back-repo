package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamScatterPointDto {
	private Long teamId;
	private String teamName;
	private String teamCode;
	private String teamImageUrl;
	private Integer gamesPlayed;
	private Double winRatePct;
	private Double xValue;
	private Double avgOverall;
	private Double avgKills;
	private Double avgGold;
	private Double avgObjectives;
}
