package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamBanStatDto {
	private Long championId;
	private String championNameKr;
	private String championNameEn;
	private String championImageUrl;
	private Integer banCount;
	private Double banRatePct;
}
