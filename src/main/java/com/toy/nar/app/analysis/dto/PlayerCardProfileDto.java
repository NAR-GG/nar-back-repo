package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlayerCardProfileDto {
	private String name;
	private String position;
	private String summonerName;
	private String soloRankTier;
	private String birthDate;
	private Integer gamesPlayed;
	private Double kda;
	private Double gpm;
	private Double dpm;
}
