package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamPlayerRecordDto {
	private Long playerId;
	private String playerName;
	private String playerImageUrl;
	private String position;
	private Integer gamesPlayed;
	private Integer wins;
	private Integer losses;
	private Double winRatePct;
	private Double avgKda;
	private Double avgKills;
	private Double avgDeaths;
	private Double avgAssists;
	private Integer firstKillCount;
	private Integer firstDeathCount;
	private Integer pentaKillCount;
	private Double avgKillParticipationPct;
	private Double avgDamageSharePct;
	private Double avgGoldSharePct;
	private Double avgVisionScore;
	private Double avgVisionScorePerMinute;
}
