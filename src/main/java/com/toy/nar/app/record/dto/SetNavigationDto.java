package com.toy.nar.app.record.dto;

import java.util.List;

/**
 * 세트 네비게이션 정보 DTO
 * 현재 게임의 세트 위치 및 같은 매치 내 다른 세트 정보 제공
 */
public record SetNavigationDto(
        int currentSet, // 현재 세트 번호
        int totalSets, // 전체 세트 수
        List<SetInfo> sets, // 모든 세트 정보
        TeamSummary blueTeam, // 블루팀 요약
        TeamSummary redTeam // 레드팀 요약
) {
    /**
     * 세트별 게임 정보
     */
    public record SetInfo(int setNumber, Long gameId) {
    }

    /**
     * 팀 요약 정보
     */
    public record TeamSummary(
            String code, // 짧은 이름 (T1, GEN)
            String name, // 긴 이름 (T1, Gen.G Esports)
            String imageUrl, // 팀 로고 URL
            int score // 세트 승점
    ) {
    }
}
