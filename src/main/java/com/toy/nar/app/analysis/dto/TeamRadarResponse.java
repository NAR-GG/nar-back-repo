package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 팀 레이더 차트 API 응답
 */
@Getter
@Builder
public class TeamRadarResponse {

    /** 팀 통계 */
    private TeamRadarStatsDto stats;

    /** 리그 평균 (비교용 점선) */
    private TeamRadarStatsDto leagueAverage;
}
