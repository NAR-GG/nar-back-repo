package com.toy.nar.app.analysis.dto;

/**
 * 팀 랭킹 개별 항목 DTO
 */
public record TeamRankingItemDto(
        int rank,
        Long teamId,
        String teamName,
        String teamCode,
        String imageUrl,
        double winRate, // 승률 (%)
        int wins,
        int losses,
        int totalGames,
        MostPickByPosition mostPicks // 라인별 모스트 픽
) {
}
