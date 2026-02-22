package com.toy.nar.app.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlayerCardFilterDto {
	private Integer year;
	private String split;
	private String patch;
	private String side;
}
