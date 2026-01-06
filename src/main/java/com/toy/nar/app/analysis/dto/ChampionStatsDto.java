package com.toy.nar.app.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChampionStatsDto {
	private String championNameKr;
	private String championNameEn;
	private long totalGames;
	private long wins;
	private double winRate; // 승률 (0.0 ~ 100.0)
	
	// JPQL 생성자
	public ChampionStatsDto(String championNameKr, String championNameEn, long totalGames, long wins) {
		this.championNameKr = championNameKr;
		this.championNameEn = championNameEn;
		this.totalGames = totalGames;
		this.wins = wins;
		this.winRate = totalGames > 0 ? (double) wins / totalGames * 100.0 : 0.0;
	}
}
