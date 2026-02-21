package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamDashboardResponse {
	private Long teamId;
	private String teamName;
	private String teamCode;
	private String teamImageUrl;
	private String leagueName;
	private TeamDashboardFilterDto appliedFilter;
	private TeamGameSummaryDto gameSummary;
	private List<TeamPlayerRecordDto> playerRecords;
	private TeamBanSideStatsDto bannedAgainst;
	private TeamBanSideStatsDto bannedByTeam;
	private TeamPlayedChampionSideStatsDto playedChampions;
}
