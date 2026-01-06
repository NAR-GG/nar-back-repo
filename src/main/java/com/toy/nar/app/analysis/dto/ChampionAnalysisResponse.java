package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChampionAnalysisResponse {
	private String patchVersion;
	private String seasonInfo; // 예: "2024 LCK Spring" 등
	private List<ChampionStatsDto> champions;
}
