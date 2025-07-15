package com.toy.nar.dto;

// JSON의 가장 바깥쪽 구조
public record ChampionApiResponse(
	String type,
	String format,
	String version,
	java.util.Map<String, ChampionData> data
) {}

