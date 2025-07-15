package com.toy.nar.dto;

public record ChampionData(
	String id,       // 영문 ID (예: "Aatrox")
	String key,      // 숫자 키 (예: "266")
	String name,     // 영문 이름 (예: "Aatrox")
	String title,
	ImageInfo image
) {}

