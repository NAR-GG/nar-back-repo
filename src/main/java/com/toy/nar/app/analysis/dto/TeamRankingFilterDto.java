package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 팀 랭킹 조회용 필터 DTO
 * 년도, 리그, 스플릿, 패치, 진영 기준 필터링
 */
@Builder
@Getter
public class TeamRankingFilterDto {
    private final Integer year;
    private final List<String> splits;
    private final List<String> leagueNames;
    private final String patch;
    /** 진영 필터: ALL, Blue, Red */
    private final String side;

    public boolean hasSideFilter() {
        return side != null && !side.equalsIgnoreCase("ALL");
    }

    public String getEffectiveSide() {
        if (side == null || side.equalsIgnoreCase("ALL")) {
            return null;
        }
        return side;
    }
}
