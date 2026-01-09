package com.toy.nar.app.analysis.dto;

import com.toy.nar.app.analysis.dto.ChampionBanStatsDto;
import com.toy.nar.app.analysis.dto.ChampionStatsDto;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChampionAnalysisResponse {
	private String patchVersion;
	private String seasonInfo; // 예: "2024 LCK Spring" 등
	private List<ChampionStatsDto> champions; // 픽률/승률 TOP 5
	private List<ChampionBanStatsDto> topBans; // 밴률 TOP 5
}
