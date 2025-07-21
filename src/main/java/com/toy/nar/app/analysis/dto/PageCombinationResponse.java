package com.toy.nar.app.analysis.dto;

import java.util.List;

import org.springframework.data.domain.Pageable;

public record PageCombinationResponse(
	List<CombinationResponseDto> content,  // 현재 페이지 데이터
	Pageable pageable,                    // 페이징 정보 (page, size, sort)
	boolean hasNext,                      // 다음 페이지 여부
	long totalCount                       // 총 개수
) { }