package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamPlayedChampionStatDto {
	private Long championId;
	private String championNameKr;
	private String championNameEn;
	private String championImageUrl;
	private Integer gamesPlayed;
	private Double winRatePct;
	private Double avgKda;
	private String lastUsedAt;
}
