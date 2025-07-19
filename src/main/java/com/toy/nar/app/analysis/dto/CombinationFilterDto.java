package com.toy.nar.app.analysis.dto;

import lombok.Builder;

@Builder
public record CombinationFilterDto(Integer year, String split, String leagueName, String teamName, String patch) {
}
