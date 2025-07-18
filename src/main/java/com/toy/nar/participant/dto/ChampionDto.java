package com.toy.nar.participant.dto;

public record ChampionDto(
	Long id,
	String championNameKr,
	String championNameEn,
	String imageUrl
) {}