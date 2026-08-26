package com.toy.nar.app.crawledcommunity.repository;

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
