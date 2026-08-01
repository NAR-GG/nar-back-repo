package com.toy.nar.app.mobile.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MobileScheduleListResponse(
		String date,
		String league,
		Long teamId,
		List<MobileMatchSummary> matches) {

	public record MobileMatchSummary(
			String matchId,
			@Schema(description = "경기 일자(KST)", example = "2026-04-01")
			String date,
			String scheduledTime,
			String matchStatus,
			String matchTitle,
			String leagueName,
			MobileTeamResult blueTeam,
			MobileTeamResult redTeam,
			String liveStreamUrl,
			@Schema(description = "라이브 중계 채널 목록. 진행 중 경기에서만 채워지며, 복수면 앱이 선택 시트를 띄운다. "
					+ "liveStreamUrl 은 하위호환용(첫 번째 링크와 동일)")
			List<MobileStreamLink> streamLinks,
			@Schema(description = "다전제 규격(1=단판, 3=3전 2선승, 5=5전 3선승). 같은 리그 안에서도 스테이지별로 다르다. "
					+ "업스트림에 값이 없으면 null", example = "3", nullable = true)
			Integer bestOf,
			@Schema(description = "매치에 속한 세트(게임) 목록. 아직 세트가 생성되지 않은 매치는 빈 배열")
			List<MobileGameSummary> games) {
	}

	public record MobileStreamLink(
			@Schema(description = "플랫폼 식별자", example = "chzzk")
			String provider,
			@Schema(description = "표시 이름", example = "치지직")
			String label,
			@Schema(description = "부가 설명", example = "LCK 공식 채널 · 한국어")
			String description,
			@Schema(description = "중계 URL", example = "https://chzzk.naver.com/9381e7d6816e6d915a44a13c0195b202")
			String url) {
	}

	public record MobileGameSummary(
			@Schema(description = "세트 순서(1부터 시작)", example = "1")
			Integer gameOrder,
			@Schema(description = "라이브/선수 평점 API에서 사용하는 esports gameId", example = "113990000000000001")
			String gameId,
			@Schema(description = "기록(record) API에서 사용하는 내부 gameId. 기록 미적재 시 null", example = "1024", nullable = true)
			Long recordGameId,
			@Schema(description = "세트 상태: LIVE(진행 중)/ENDED(종료, 데이터 보유)/SCHEDULED(예정). 일정 목록에서는 null", example = "LIVE", nullable = true)
			String status,
			@Schema(description = "세트 다시보기 VOD URL(주로 한국어 유튜브). 없으면 null. 일정 목록에서는 null", example = "https://youtu.be/abc?t=10", nullable = true)
			String vodUrl,
			@Schema(description = "세트 승리 팀 코드. blueTeam.teamCode/redTeam.teamCode 와 같은 문자열이다. "
					+ "status 가 ENDED 가 아니거나 승자를 아직 확정하지 못했으면 null. 일정 목록에서는 null",
					example = "T1", nullable = true)
			String winnerTeamCode) {
	}

	public record MobileTeamResult(
			String teamName,
			String teamCode,
			String teamImageUrl,
			int score) {
	}
}
