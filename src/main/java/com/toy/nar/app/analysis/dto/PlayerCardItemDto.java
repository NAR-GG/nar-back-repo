package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlayerCardItemDto {
	private Long playerId;
	private String playerName;
	private String playerImageUrl;
	private String teamCode;
	private String teamImageUrl;
	private List<PlayerCardChampionDto> mostChampions;
	private PlayerCardProfileDto profile;
}
