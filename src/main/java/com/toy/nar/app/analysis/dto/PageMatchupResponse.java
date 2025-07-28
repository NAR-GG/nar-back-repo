package com.toy.nar.app.analysis.dto;

import java.util.List;

import org.springframework.data.domain.Pageable;

public record PageMatchupResponse(
	int totalMatches,                     // 총 매치업 횟수
	double winRateForChampion1,           // champion1의 승률 (%)
	List<CombinationDetailDto.GameDetailDto> content,          // 현재 페이지의 게임 기록 리스트 (최신순)
	Pageable pageable,                    // 페이징 정보 (page, size, sort)
	boolean hasNext,                      // 다음 페이지 여부
	long totalCount                       // 총 게임 기록 개수 (페이징 대상)
) { }
