package com.toy.nar.app.analysis.dto;

/**
 * 상대 챔피언 정보 DTO (모스트 픽 호버 시 표시)
 */
public record OpponentChampionDto(
        String championName,
        String championImageUrl,
        int matchCount,
        double winRate) {
}
