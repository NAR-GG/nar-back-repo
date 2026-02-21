package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamDetailStatsResponse {
	private String leagueName;
	private Integer year;
	private Integer totalTeams;
	private List<TeamDetailStatsItemDto> items;
}
