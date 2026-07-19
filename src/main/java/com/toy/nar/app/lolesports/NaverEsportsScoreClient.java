package com.toy.nar.app.lolesports;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 네이버 e스포츠 비공식 API에서 매치 세트 스코어를 조회한다.
 *
 * <p>Riot getEventDetails 의 gameWins 는 다음 세트 픽밴 시작에야 뒤집히지만(실측 46초~5분+,
 * 마지막 세트는 25분+), 네이버는 세트 종료 직후 반영된다. SET_END 푸시 스코어 보정 전용
 * 보조 소스 — 세트 종료 시점에만 호출되고(하루 수십 콜), 실패하면 조용히 null 을 돌려
 * 기존 Riot 경로로 폴백한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverEsportsScoreClient {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final WebClient webClient;

	/** 비공식 API 킬스위치. 스키마 변경·차단 시 끄면 Riot 경로만 쓴다. */
	@Value("${live.notification.set-end-score.naver-enabled:true}")
	private boolean enabled;

	/**
	 * 팀 코드(블루/레드)와 경기 시작 시각(UTC)으로 네이버 세트 스코어 [blue, red] 조회.
	 * 미커버 리그·매칭 실패·API 오류 모두 null.
	 */
	public int[] fetchScore(String blueTeamCode, String redTeamCode, LocalDateTime matchDateUtc) {
		if (!enabled || blueTeamCode == null || redTeamCode == null || matchDateUtc == null) {
			return null;
		}
		try {
			// 네이버는 경기 시작일(KST) 기준으로 묶는다 — 자정 넘겨 끝나도 시작일로 조회.
			String day = matchDateUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(KST)
					.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
			JsonNode root = webClient.get()
					.uri(uri -> uri
							.scheme("https")
							.host("esports-api.game.naver.com")
							.path("/service/v2/schedule/day")
							.queryParam("day", day)
							.build())
					.header("User-Agent", "Mozilla/5.0")
					.retrieve()
					.bodyToMono(JsonNode.class)
					.timeout(Duration.ofSeconds(3))
					.block();
			return extractScore(root, blueTeamCode, redTeamCode);
		} catch (Exception e) {
			log.warn("Naver esports score fetch failed. blue={} red={}: {}",
					blueTeamCode, redTeamCode, e.getMessage());
			return null;
		}
	}

	/**
	 * day 응답에서 팀 약칭 쌍으로 경기를 찾아 [blue, red] 스코어 반환.
	 * 네이버 home/away 순서가 우리 blue/red 와 다르면 스왑한다. 시작 전 경기는 무시.
	 */
	static int[] extractScore(JsonNode root, String blueTeamCode, String redTeamCode) {
		if (root == null) {
			return null;
		}
		for (JsonNode match : root.path("content").path("matches")) {
			if ("BEFORE".equalsIgnoreCase(match.path("matchStatus").asText())) {
				continue;
			}
			String home = match.path("homeTeam").path("nameEngAcronym").asText("");
			String away = match.path("awayTeam").path("nameEngAcronym").asText("");
			if (home.isEmpty() || away.isEmpty()) {
				continue;
			}
			int homeScore = match.path("homeScore").asInt(0);
			int awayScore = match.path("awayScore").asInt(0);
			if (home.equalsIgnoreCase(blueTeamCode) && away.equalsIgnoreCase(redTeamCode)) {
				return new int[] { homeScore, awayScore };
			}
			if (home.equalsIgnoreCase(redTeamCode) && away.equalsIgnoreCase(blueTeamCode)) {
				return new int[] { awayScore, homeScore };
			}
		}
		return null;
	}
}
