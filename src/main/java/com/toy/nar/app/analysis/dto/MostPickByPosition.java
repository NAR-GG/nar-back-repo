package com.toy.nar.app.analysis.dto;

import java.util.List;

/**
 * 라인별 모스트 픽 챔피언 DTO
 * 동률인 경우 리스트에 여러 챔피언이 포함됨
 */
public record MostPickByPosition(
        List<ChampionPickDto> top,
        List<ChampionPickDto> jungle,
        List<ChampionPickDto> mid,
        List<ChampionPickDto> bot,
        List<ChampionPickDto> support) {
}
