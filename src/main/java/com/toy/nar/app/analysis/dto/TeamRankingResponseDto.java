package com.toy.nar.app.analysis.dto;

import java.util.List;

/**
 * 팀 랭킹 API 응답 DTO
 */
public record TeamRankingResponseDto(
        List<TeamRankingItemDto> rankings,
        int totalTeams,
        TeamRankingFilterDto appliedFilter) {
}
