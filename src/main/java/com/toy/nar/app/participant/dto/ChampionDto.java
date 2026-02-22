package com.toy.nar.app.participant.dto;

public record ChampionDto(
	Long id,
	String championNameKr,
	String championNameEn,
	String imageUrl,
	String loadingImageUrl
) {}
