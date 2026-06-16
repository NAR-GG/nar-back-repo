package com.toy.nar.app.mobile.match.dto;

/**
 * 라이브 경기상세 챔피언 화면에서 밴을 진영별로 채우기 위한 배치 밴 조회 프로젝션.
 *
 * <p>라이브 피드에는 밴 정보가 없어, reconcile 된 배치 game 의 {@code bans} 테이블에서
 * 가져온다. {@code side} 는 {@code game_participants.side} 를 대문자화한 'BLUE'/'RED'.
 */
public interface LiveBanRow {

	/** 'BLUE' 또는 'RED' (game_participants.side 대문자). */
	String getSide();

	/** 영문 챔피언명 (라이브 픽과 표기 일관). */
	String getChampionName();

	/** 챔피언 이미지 URL. 없으면 null (Flutter 가 Data Dragon 으로 폴백). */
	String getImageUrl();
}
