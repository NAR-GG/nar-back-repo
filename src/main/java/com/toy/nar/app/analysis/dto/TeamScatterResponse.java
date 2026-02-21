package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamScatterResponse {
	private String leagueName;
	private Integer year;
	private TeamScatterMetric metric;
	private String xAxisLabel;
	private String yAxisLabel;
	private Double xLeagueAverage;
	private Double yLeagueAverage;
	private List<TeamScatterPointDto> points;
}
