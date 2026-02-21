package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamGameSummaryDto {
	private Integer matchesPlayed;
	private Integer matchWins;
	private Integer matchLosses;
	private Integer setsPlayed;
	private Integer setWins;
	private Integer setLosses;
	private Double winRatePct;
	private Double avgKills;
	private Double avgGold;
	private Double avgBarons;
	private Double avgDragons;
	private Double avgTowers;
	private Integer firstBloodCount;
	private Integer firstTowerCount;
	private Integer firstDragonCount;
	private Integer firstHeraldCount;
	private Integer firstBaronCount;
	private Double firstBloodRatePct;
	private Double firstTowerRatePct;
	private Double firstDragonRatePct;
	private Double firstHeraldRatePct;
	private Double firstBaronRatePct;
}
