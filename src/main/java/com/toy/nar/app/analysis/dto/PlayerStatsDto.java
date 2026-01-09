package com.toy.nar.app.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStatsDto {
	private String teamName;
	private String playerName;
	private String playerImgUrl;
	private long totalGames;
	private double statValue; // KDA, GPM, or DPM value
}
