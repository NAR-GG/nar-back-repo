package com.toy.nar.dto;

import lombok.Builder;

@Builder
public record CombinationFilterDto(Integer year, String split, String leagueName, String teamName) {
}
