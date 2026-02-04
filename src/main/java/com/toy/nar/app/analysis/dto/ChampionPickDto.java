package com.toy.nar.app.analysis.dto;

import java.util.List;

/**
 * 챔피언 픽 정보 DTO (호버 시 상세 정보용)
 */
public record ChampionPickDto(
                String championName,
                String championImageUrl,
                int playCount, // 플레이 수
                double winRate, // 해당 챔피언 승률 (%)
                double banRate, // 해당 챔피언 밴률 (%)
                List<OpponentChampionDto> topOpponents // 상대 챔피언 모스트 3
) {
        /**
         * 상대 챔피언 정보 없이 생성하는 팩토리 메서드
         */
        public static ChampionPickDto withoutOpponents(
                        String championName,
                        String championImageUrl,
                        int playCount,
                        double winRate,
                        double banRate) {
                return new ChampionPickDto(championName, championImageUrl, playCount, winRate, banRate, List.of());
        }

        /**
         * 상대 챔피언 정보를 추가한 새 인스턴스 반환
         */
        public ChampionPickDto withOpponents(List<OpponentChampionDto> opponents) {
                return new ChampionPickDto(championName, championImageUrl, playCount, winRate, banRate, opponents);
        }
}
