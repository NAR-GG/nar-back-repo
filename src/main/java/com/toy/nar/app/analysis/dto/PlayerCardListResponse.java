package com.toy.nar.app.analysis.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlayerCardListResponse {
	private String leagueName;
	private PlayerCardFilterDto appliedFilter;
	private Integer page;
	private Integer size;
	private Long totalCount;
	private Integer totalPages;
	private List<PlayerCardItemDto> players;
}
