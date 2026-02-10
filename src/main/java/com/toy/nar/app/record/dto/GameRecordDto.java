package com.toy.nar.app.record.dto;

import java.util.List;

// mockGameData의 최상위 구조
public record GameRecordDto(
		String gameid,
		String datacompleteness,
		String league,
		int year,
		String split,
		int playoffs,
		String date,
		int game,
		String patch,
		int gamelength,
		BansDto bans,
		FearlessDto fearless, // 피어리스 드래프트 (이전 세트 픽 챔피언)
		List<PlayerRecordDto> players,
		SetNavigationDto setNav // 세트 네비게이션 정보
) {
}