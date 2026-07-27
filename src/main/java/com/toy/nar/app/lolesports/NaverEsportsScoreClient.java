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

	/** 같은 팀 쌍이 하루 2경기(더블헤더 등)일 때 오매칭을 막는 시작 시각 허용 오차. */
	private static final long START_TIME_TOLERANCE_MS = Duration.ofHours(6).toMillis();

	/** 네이버 매치 조회 결과. finished 는 matchStatus=RESULT(매치 종료 확정). */
	public record Result(int[] score, boolean finished) {
	}

	/**
	 * 팀 코드(블루/레드)와 경기 시작 시각(UTC)으로 네이버 세트 스코어 [blue, red] 조회.
	 * 미커버 리그·매칭 실패·API 오류 모두 null.
	 */
	public int[] fetchScore(String blueTeamCode, String redTeamCode, LocalDateTime matchDateUtc) {
		Result result = fetchResult(blueTeamCode, redTeamCode, matchDateUtc);
		return result == null ? null : result.score();
	}

	/**
	 * 스코어와 함께 매치 종료 여부(matchStatus=RESULT)까지 돌려준다. 업스트림 lolesports 가
	 * 종료를 늦게 반영하는 구간(실측 17분+)에서 종료 확정용으로 쓴다.
	 */
	public Result fetchResult(String blueTeamCode, String redTeamCode, LocalDateTime matchDateUtc) {
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
			long startEpochMs = matchDateUtc.toInstant(ZoneOffset.UTC).toEpochMilli();
			Result result = extractResult(root, blueTeamCode, redTeamCode, startEpochMs);
			if (result == null) {
				// 미커버 리그·약칭 불일치는 조용히 폴백되면 원인을 못 찾는다 — 흔적을 남긴다.
				log.info("Naver esports match not found. blue={} red={} day={}", blueTeamCode, redTeamCode, day);
			}
			return result;
		} catch (Exception e) {
			log.warn("Naver esports score fetch failed. blue={} red={}: {}",
					blueTeamCode, redTeamCode, e.getMessage());
			return null;
		}
	}

	/** {@link #extractResult} 의 스코어만. */
	static int[] extractScore(JsonNode root, String blueTeamCode, String redTeamCode, long matchStartEpochMs) {
		Result result = extractResult(root, blueTeamCode, redTeamCode, matchStartEpochMs);
		return result == null ? null : result.score();
	}

	/**
	 * day 응답에서 LoL 종목 + 팀 약칭 쌍 + 시작 시각 근접(±6시간)으로 경기를 찾아
	 * [blue, red] 스코어 + 종료 여부 반환. 같은 팀 쌍이 여러 경기면 시작 시각이 가장 가까운 경기를 쓴다.
	 * 네이버 home/away 순서가 우리 blue/red 와 다르면 스왑한다. 시작 전 경기는 무시.
	 */
	static Result extractResult(JsonNode root, String blueTeamCode, String redTeamCode, long matchStartEpochMs) {
		if (root == null) {
			return null;
		}
		Result best = null;
		long bestGap = Long.MAX_VALUE;
		for (JsonNode match : root.path("content").path("matches")) {
			// 네이버 day 응답은 전 종목 포함 — 같은 조직이 타 종목에서 같은 날 붙으면 오매칭된다.
			if (!"lol".equalsIgnoreCase(match.path("gameCode").asText())) {
				continue;
			}
			String matchStatus = match.path("matchStatus").asText();
			if ("BEFORE".equalsIgnoreCase(matchStatus)) {
				continue;
			}
			String home = match.path("homeTeam").path("nameEngAcronym").asText("");
			String away = match.path("awayTeam").path("nameEngAcronym").asText("");
			if (home.isEmpty() || away.isEmpty()) {
				continue;
			}
			int homeScore = match.path("homeScore").asInt(0);
			int awayScore = match.path("awayScore").asInt(0);
			int[] score;
			if (home.equalsIgnoreCase(blueTeamCode) && away.equalsIgnoreCase(redTeamCode)) {
				score = new int[] { homeScore, awayScore };
			} else if (home.equalsIgnoreCase(redTeamCode) && away.equalsIgnoreCase(blueTeamCode)) {
				score = new int[] { awayScore, homeScore };
			} else {
				continue;
			}
			long gap = Math.abs(match.path("startDate").asLong(Long.MIN_VALUE) - matchStartEpochMs);
			if (gap <= START_TIME_TOLERANCE_MS && gap < bestGap) {
				best = new Result(score, "RESULT".equalsIgnoreCase(matchStatus));
				bestGap = gap;
			}
		}
		return best;
	}
}
