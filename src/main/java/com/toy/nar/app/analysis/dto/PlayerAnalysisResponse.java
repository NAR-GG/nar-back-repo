package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PlayerAnalysisResponse {
	private String patchVersion;
	private String seasonInfo;
	private List<PlayerStatsDto> kdaTop5;
	private List<PlayerStatsDto> gpmTop5;
	private List<PlayerStatsDto> dpmTop5;
}
