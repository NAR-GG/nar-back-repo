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
	List<PlayerRecordDto> players
) {}