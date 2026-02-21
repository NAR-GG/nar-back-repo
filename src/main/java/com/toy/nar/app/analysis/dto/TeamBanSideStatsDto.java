package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamBanSideStatsDto {
	private List<TeamBanStatDto> all;
	private List<TeamBanStatDto> blue;
	private List<TeamBanStatDto> red;
}
