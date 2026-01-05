package com.toy.nar.app.community.repository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommunityType {
	OPGG("OP.GG"),
	INVEN("Inven"),
	NAVER("Naver Lounge");

	private final String description;
}
