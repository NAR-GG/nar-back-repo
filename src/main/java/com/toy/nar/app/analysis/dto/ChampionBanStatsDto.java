package com.toy.nar.app.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChampionBanStatsDto {
	private String championNameKr;
	private String championNameEn;
	private long banCount;
	private double banRate; // 밴률 (0.0 ~ 100.0)

	// 픽/승률 정보 (나중에 채워넣을 필드)
	private long totalGames;
	private long wins;
	private double winRate;

	// JPQL 생성자 (밴 카운트용)
	public ChampionBanStatsDto(String championNameKr, String championNameEn, long banCount) {
		this.championNameKr = championNameKr;
		this.championNameEn = championNameEn;
		this.banCount = banCount;
	}

	// 픽/승률 데이터를 채우는 메서드
	public void setPickWinStats(ChampionStatsDto stats) {
		if (stats != null) {
			this.totalGames = stats.getTotalGames();
			this.wins = stats.getWins();
			this.winRate = stats.getWinRate();
		}
	}
	
	public void calculateBanRate(long totalPatchGames) {
		this.banRate = totalPatchGames > 0 ? (double) this.banCount / totalPatchGames * 100.0 : 0.0;
	}
}
