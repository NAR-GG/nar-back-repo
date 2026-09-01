package com.toy.nar.app.mobile.match.dto;

import java.util.List;

/**
 * 라이브 경기 "경기 데이터" 탭 응답 — 챔피언 픽/밴 + 선수 스코어보드 + 팀 요약 + 오브젝트 집계.
 *
 * <p>한 화면이 세로로 쌓는 네 블록을 한 응답으로 내린다(호출 1회). 픽/밴 외 필드는 나중에 추가된
 * 것이라 구버전 앱은 무시한다.
 */
public record LiveGameChampionsResponse(
		String gameId,
		TeamChampions blueTeam,
		TeamChampions redTeam,
		Objectives objectives) {

	public record TeamChampions(
			String teamName,
			List<Pick> picks,
			List<Ban> bans,
			TeamSummary summary) {
	}

	public record Pick(
			String position,
			String championName,
			String championImageUrl,
			String playerName,
			Integer level,
			Integer kills,
			Integer deaths,
			Integer assists,
			Integer creepScore,
			Integer totalGoldEarned,
			/** 0~1. 분모는 자기 팀 총 킬. */
			Double killParticipation,
			/** 0~1. 분모는 자기 팀 5명 딜 합 — 상대 팀과 비교할 수 없다. */
			Double championDamageShare,
			/** 구매 순서대로. 최대 8개(슬롯 7 + 장신구)이고, 하위템 잔재로 더 길 수 있다. */
			List<String> itemImageUrls,
			String keystoneIconUrl,
			String subStyleIconUrl) {
	}

	public record Ban(
			String championName,
			String championImageUrl) {
	}

	/** 팀 합산. 전부 참가자 값 합산이라 추가 조회가 없다. */
	public record TeamSummary(
			Integer kills,
			Integer deaths,
			Integer assists,
			Integer creepScore,
			Integer totalGoldEarned) {
	}

	/**
	 * 팀별 오브젝트 획득 수. 라이브 피드가 주는 전부이며 전령·유충·아타칸은 피드에 없다.
	 *
	 * <p>장로용은 드래곤 수에서 분리한다 — 원천 이벤트의 {@code value_after} 는 장로를 드래곤
	 * 카운터에 포함해 세므로 그 값을 그대로 쓰면 안 된다.
	 */
	public record Objectives(
			TeamObjectives blueTeam,
			TeamObjectives redTeam) {
	}

	public record TeamObjectives(
			Integer dragons,
			/** 획득 순서대로의 드래곤 속성(infernal/ocean/cloud/mountain/hextech/chemtech). 장로는 제외. */
			List<String> dragonTypes,
			Integer elders,
			Integer barons,
			Integer towers,
			Integer inhibitors) {
	}
}
