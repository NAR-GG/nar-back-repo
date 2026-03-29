package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamProfileHeaderResponse {
	private Long teamId;
	private String teamName;
	private String teamCode;
	private String teamImageUrl;
	private TeamSocialLinks socialLinks;
	private List<TeamRecentMatchDto> recentMatches;
}
