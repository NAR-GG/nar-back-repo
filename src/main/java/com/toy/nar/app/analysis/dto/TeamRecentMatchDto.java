package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamRecentMatchDto {
	private String matchId;
	private String leagueName;
	private String state;
	private String scheduledAt;
	private String relativeLabel;
	private String blueTeamCode;
	private String blueTeamName;
	private String redTeamCode;
	private String redTeamName;
	private Integer blueScore;
	private Integer redScore;
}
