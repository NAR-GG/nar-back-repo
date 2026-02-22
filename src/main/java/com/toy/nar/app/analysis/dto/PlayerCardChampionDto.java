package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlayerCardChampionDto {
	private Long championId;
	private String championNameKr;
	private String championNameEn;
	private String championImageUrl;
	private String championLoadingImageUrl;
	private Integer playCount;
	private Double winRatePct;
}
