package com.toy.nar.app.mobile.match.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 라이브 경기 "경기 데이터" 탭 응답 — 챔피언 픽/밴 + 선수 스코어보드 + 팀 요약 + 오브젝트 집계.
 *
 * <p>한 화면이 세로로 쌓는 네 블록을 한 응답으로 내린다(호출 1회). 픽/밴 외 필드는 나중에 추가된
 * 것이라 구버전 앱은 무시한다.
 */
public record LiveGameChampionsResponse(
		String gameId,
		/** 이 응답이 기준한 프레임 시각(UTC). 라이브면 최신 폴링, 종료 경기면 마지막 분 스냅샷. "N분 기준" 라벨용. */
		LocalDateTime frameTimestampUtc,
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
			/** 구매 순서대로 섞여 있는 원본 목록. 섹션이 필요하면 아래 4개를 쓴다. */
			List<String> itemImageUrls,
			/** 장비 코어. 최대 6개. */
			List<String> coreItemImageUrls,
			/** 2026 바텀 퀘스트 보상으로 7번째 칸에 옮겨진 신발. 퀘스트 미완이면 null(신발은 코어에 있다). */
			String questItemImageUrl,
			/** 장신구(3340·3363·3364) 한 칸. 안 샀으면 null. */
			String trinketItemImageUrl,
			/** 제어와드·물약·영약. 없으면 빈 목록. */
			List<String> consumableItemImageUrls,
			String keystoneIconUrl,
			String subStyleIconUrl,
			/** 빌드 시트용 룬 전체. perks 가 없으면 null. */
			RuneBuild runes) {
	}

	public record RuneBuild(
			RuneTree primary,
			RuneTree sub,
			/** 파편. 같은 파편을 두 칸에 찍으면 피드가 하나로 합쳐 2개가 된다(25%). 고정 3칸으로 그리면 안 된다. */
			List<Shard> shards) {
	}

	public record RuneTree(
			String styleName,
			String styleIconUrl,
			/** 주 트리 4개(핵심룬이 첫 원소) / 부 트리 2개. 슬롯 순. */
			List<Rune> runes) {
	}

	public record Rune(
			String name,
			String iconUrl,
			/** ddragon shortDesc 평문. 툴팁용. */
			String description) {
	}

	public record Shard(
			String name,
			String iconUrl,
			/** 수치. "+9", "+10~180". 칩 라벨 = name + " " + label */
			String label) {
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
