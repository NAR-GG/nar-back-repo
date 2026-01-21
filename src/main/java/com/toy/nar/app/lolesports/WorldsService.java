package com.toy.nar.app.lolesports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorldsService {

	private final WebClient webClient;

	private static final java.util.Map<String, String> LEAGUE_IDS = java.util.Map.of(
			"LCK", "98767991310872058",
			"LPL", "98767991314006698",
			"LEC", "98767991302996019",
			"LCS", "98767991299243165",
			"PCS", "98767991332355509",
			"VCS", "98767991349978712",
			"WORLDS", "98767975604431411",
			"MSI", "98767991325878492");

	@Value("${lolesports.riot-api.key}")
	private String RIOT_API_KEY;

	public MatchResponseWrapper getWorldsMatches(String pageToken, String leagueSlug) {
		String leagueId = LEAGUE_IDS.getOrDefault(leagueSlug != null ? leagueSlug.toUpperCase() : "LCK",
				LEAGUE_IDS.get("LCK"));

		// 1. [Schedule API] 전체 일정 조회 (이건 한 번만 하니까 block 해도 됨)
		JsonNode scheduleRoot = callScheduleApi(pageToken, leagueId);
		if (scheduleRoot == null)
			return MatchResponseWrapper.builder().matches(List.of()).build();

		JsonNode dataNode = scheduleRoot.path("data").path("schedule");
		JsonNode eventsNode = dataNode.path("events");
		String nextToken = dataNode.path("pages").path("older").asText(null);

		List<JsonNode> targetEvents = new ArrayList<>();
		if (eventsNode.isArray()) {
			for (JsonNode event : eventsNode) {
				// 모든 매치 필터링 (state 체크 제거)
				if ("match".equalsIgnoreCase(event.path("type").asText())) {
					targetEvents.add(event);
				}
			}
		}

		// 2. [병렬 처리 핵심] Flux를 사용하여 동시에 API 쏘기 (동시성 제한 추가)
		List<MatchResultDto> matchResults = reactor.core.publisher.Flux.fromIterable(targetEvents)
				.flatMap(event -> {
					String matchId = event.path("match").path("id").asText();
					String startTime = event.path("startTime").asText();
					String blockName = event.path("blockName").asText();
					String state = event.path("state").asText("unstarted"); // 상태 추출

					// 비동기로 상세 정보 가져오기
					return fetchMatchDetailsAsync(matchId, startTime, blockName, state);
				}, 5) // 동시 실행 수 5개로 제한 (시스템 부하 방지)
				// 날짜 내림차순 정렬
				.sort((a, b) -> b.getMatchDate().compareTo(a.getMatchDate()))
				.collectList()
				.block(); // 모든 작업이 끝날 때까지 대기

		return MatchResponseWrapper.builder()
				.matches(matchResults)
				.nextPageToken(nextToken)
				.build();
	}

	private reactor.core.publisher.Mono<MatchResultDto> fetchMatchDetailsAsync(String eventId, String matchDate,
			String stageName, String matchState) {
		return webClient.get()
				.uri(uri -> uri
						.scheme("https")
						.host("esports-api.lolesports.com")
						.path("/persisted/gw/getEventDetails")
						.queryParam("hl", "ko-KR")
						.queryParam("id", eventId)
						.build())
				.header("x-api-key", "0TvQnueqKa5mxJntVWt0w4LpLfEkrV1Ta8rQBb9Z")
				.header("Referer", "https://lolesports.com/")
				.retrieve()
				.bodyToMono(JsonNode.class)
				.map(root -> {
					// 기존 fetchMatchDetails 내부 로직과 동일하게 파싱
					JsonNode event = root.path("data").path("event");
					JsonNode match = event.path("match");
					JsonNode teams = match.path("teams");

					// [수정] 스케줄 목록에서 가져온 state 우선 사용
					String apiState = event.path("state").asText("unstarted");
					String finalState = (matchState != null && !matchState.isEmpty()) ? matchState : apiState;

					if (teams.size() < 2)
						return null; // 유효하지 않은 데이터

					JsonNode teamA = teams.get(0);
					JsonNode teamB = teams.get(1);
					int winsA = teamA.path("result").path("gameWins").asInt(0);
					int winsB = teamB.path("result").path("gameWins").asInt(0);

					String imageA = teamA.path("image").asText("");
					String imageB = teamB.path("image").asText("");
					if (imageA.startsWith("http:"))
						imageA = imageA.replace("http:", "https:");
					if (imageB.startsWith("http:"))
						imageB = imageB.replace("http:", "https:");

					List<MatchResultDto.SetVod> setVods = new ArrayList<>();
					JsonNode games = match.path("games");
					int setNum = 1;

					if (games.isArray()) {
						for (JsonNode game : games) {
							if (!"completed".equalsIgnoreCase(game.path("state").asText()))
								continue;
							String vodUrl = findBestVodUrl(game.path("vods"));

							setVods.add(MatchResultDto.SetVod.builder()
									.setNumber(setNum++)
									.vodUrl(vodUrl)
									.build());
						}
					}

					// [보정 1] 상태가 unstarted인데 VOD가 있다면 completed로 강제 변경 (VOD 우선)
					if ("unstarted".equalsIgnoreCase(finalState) && !setVods.isEmpty()) {
						finalState = "completed";
					}

					// [보정 2] 상태가 completed인데, 실제 게임들이 모두 unstarted이고 VOD도 없다면 unstarted로 정정 (LPL 가짜
					// 완료 방지)
					boolean allGamesUnstarted = true;
					if (games.isArray() && games.size() > 0) {
						for (JsonNode game : games) {
							if (!"unstarted".equalsIgnoreCase(game.path("state").asText())) {
								allGamesUnstarted = false;
								break;
							}
						}
						// 게임 정보는 있는데 모두 unstarted이고 VOD도 없으면 -> 아직 시작 안 한 것
						if (allGamesUnstarted && setVods.isEmpty() && "completed".equalsIgnoreCase(finalState)) {
							finalState = "unstarted";
						}
					}

					// DTO 생성 후 반환
					String liveStreamUrl = findBestLiveStreamUrl(event.path("streams"));
					return MatchResultDto.builder()
							.matchId(eventId)
							.matchTitle(stageName + " | " + teamA.path("code").asText() + " vs "
									+ teamB.path("code").asText())
							.matchDate(matchDate)
							.state(finalState) // 상태 설정
							.score(winsA + " : " + winsB)
							.blueTeam(MatchResultDto.TeamInfo.builder()
									.code(teamA.path("code").asText())
									.name(teamA.path("name").asText())
									.imageUrl(imageA)
									.wins(winsA).build())
							.redTeam(MatchResultDto.TeamInfo.builder()
									.code(teamB.path("code").asText())
									.name(teamB.path("name").asText())
									.imageUrl(imageB)
									.wins(winsB).build())
							.sets(setVods)
							.liveStreamUrl(liveStreamUrl)
							.build();
				})
				.onErrorResume(e -> {
					log.error("상세 조회 실패 (ID: {}): {}", eventId, e.getMessage());
					return reactor.core.publisher.Mono.empty(); // 에러 난 건 리스트에서 제외
				});
	}

	public List<MatchResultDto> getRecent3Matches(String leagueSlug) {
		String leagueId = LEAGUE_IDS.getOrDefault(leagueSlug != null ? leagueSlug.toUpperCase() : "LCK",
				LEAGUE_IDS.get("LCK"));
		// 1. [Schedule API] 전체 일정 조회
		JsonNode scheduleRoot = callApi("/persisted/gw/getSchedule", "leagueId", leagueId);
		if (scheduleRoot == null)
			return List.of();

		List<JsonNode> events = new ArrayList<>();
		JsonNode eventsNode = scheduleRoot.path("data").path("schedule").path("events");

		if (eventsNode.isArray()) {
			for (JsonNode event : eventsNode) {
				// 완료된 '매치'만 필터링 (type check 추가 권장)
				if ("match".equalsIgnoreCase(event.path("type").asText())) {
					events.add(event);
				}
			}
		}

		// 최신순 정렬 -> 상위 3개 추출
		// 여기서 startTime도 같이 가져가는 게 좋습니다.
		return events.stream()
				.sorted(Comparator.comparing(e -> e.path("startTime").asText(), Comparator.reverseOrder()))
				.limit(3)
				.map(event -> {
					String matchId = event.path("match").path("id").asText();
					String matchDate = event.path("startTime").asText(); // [수정] 날짜 추출
					String state = event.path("state").asText("unstarted"); // [수정] 상태 추출
					return fetchMatchDetails(matchId, matchDate, state); // [수정] 날짜, 상태 전달
				})
				.filter(dto -> dto != null) // 혹시 모를 null 제거
				.toList();
	}

	// [수정] matchDate, matchState를 인자로 받음
	private MatchResultDto fetchMatchDetails(String eventId, String matchDate, String matchState) {
		JsonNode root = callApi("/persisted/gw/getEventDetails", "id", eventId);
		if (root == null)
			return null;

		JsonNode event = root.path("data").path("event");
		JsonNode match = event.path("match");
		JsonNode teams = match.path("teams");

		String apiState = event.path("state").asText("unstarted");
		String finalState = (matchState != null && !matchState.isEmpty()) ? matchState : apiState;

		if (teams.size() < 2)
			return null;

		// 팀 정보 파싱
		JsonNode teamA = teams.get(0);
		JsonNode teamB = teams.get(1);
		int winsA = teamA.path("result").path("gameWins").asInt(0);
		int winsB = teamB.path("result").path("gameWins").asInt(0);

		String imageA = teamA.path("image").asText("");
		String imageB = teamB.path("image").asText("");
		if (imageA.startsWith("http:"))
			imageA = imageA.replace("http:", "https:");
		if (imageB.startsWith("http:"))
			imageB = imageB.replace("http:", "https:");

		// 세트별 VOD 파싱
		List<MatchResultDto.SetVod> setVods = new ArrayList<>();
		JsonNode games = match.path("games");
		int setNum = 1;

		if (games.isArray()) {
			for (JsonNode game : games) {
				// 게임이 완료된 것만 (unneeded 제외)
				if (!"completed".equalsIgnoreCase(game.path("state").asText()))
					continue;

				String vodUrl = findBestVodUrl(game.path("vods"));

				setVods.add(MatchResultDto.SetVod.builder()
						.setNumber(setNum++)
						.vodUrl(vodUrl)
						.build());
			}
		}

		// [보정 1] 상태가 unstarted인데 VOD가 있다면 completed로 강제 변경
		if ("unstarted".equalsIgnoreCase(finalState) && !setVods.isEmpty()) {
			finalState = "completed";
		}

		// [보정 2] 상태가 completed인데, 실제 게임들이 모두 unstarted이고 VOD도 없다면 unstarted로 정정 (LPL 가짜
		// 완료 방지)
		boolean allGamesUnstarted = true;
		if (games.isArray() && games.size() > 0) {
			for (JsonNode game : games) {
				if (!"unstarted".equalsIgnoreCase(game.path("state").asText())) {
					allGamesUnstarted = false;
					break;
				}
			}
			if (allGamesUnstarted && setVods.isEmpty() && "completed".equalsIgnoreCase(finalState)) {
				finalState = "unstarted";
			}
		}

		return MatchResultDto.builder()
				.matchId(eventId)
				.matchTitle(teamA.path("code").asText() + " vs " + teamB.path("code").asText())
				.matchDate(matchDate) // [수정] 넘겨받은 날짜 사용
				.state(finalState) // 상태 설정
				.score(winsA + " : " + winsB)
				.blueTeam(MatchResultDto.TeamInfo.builder()
						.code(teamA.path("code").asText())
						.name(teamA.path("name").asText())
						.imageUrl(imageA)
						.wins(winsA)
						.build())
				.redTeam(MatchResultDto.TeamInfo.builder()
						.code(teamB.path("code").asText())
						.name(teamB.path("name").asText())
						.imageUrl(imageB)
						.wins(winsB)
						.build())
				.sets(setVods)
				.build();
	}

	private MatchResultDto fetchMatchDetails(String eventId, String matchDate, String stageName, String matchState) {
		JsonNode root = callApi("/persisted/gw/getEventDetails", "id", eventId);
		if (root == null)
			return null;

		JsonNode event = root.path("data").path("event");
		JsonNode match = event.path("match");
		JsonNode teams = match.path("teams");

		if (teams.size() < 2)
			return null;

		JsonNode teamA = teams.get(0);
		JsonNode teamB = teams.get(1);
		int winsA = teamA.path("result").path("gameWins").asInt(0);
		int winsB = teamB.path("result").path("gameWins").asInt(0);

		List<MatchResultDto.SetVod> setVods = new ArrayList<>();
		JsonNode games = match.path("games");
		int setNum = 1;

		if (games.isArray()) {
			for (JsonNode game : games) {
				if (!"completed".equalsIgnoreCase(game.path("state").asText()))
					continue;
				String vodUrl = findBestVodUrl(game.path("vods"));
				setVods.add(MatchResultDto.SetVod.builder().setNumber(setNum++).vodUrl(vodUrl).build());
			}
		}

		String apiState = event.path("state").asText("unstarted");
		String finalState = (matchState != null && !matchState.isEmpty()) ? matchState : apiState;

		// [보정 1] 상태가 unstarted인데 VOD가 있다면 completed로 강제 변경
		if ("unstarted".equalsIgnoreCase(finalState) && !setVods.isEmpty()) {
			finalState = "completed";
		}

		// [보정 2] 상태가 completed인데, 실제 게임들이 모두 unstarted이고 VOD도 없다면 unstarted로 정정 (LPL 가짜
		// 완료 방지)
		boolean allGamesUnstarted = true;
		if (games.isArray() && games.size() > 0) {
			for (JsonNode game : games) {
				if (!"unstarted".equalsIgnoreCase(game.path("state").asText())) {
					allGamesUnstarted = false;
					break;
				}
			}
			if (allGamesUnstarted && setVods.isEmpty() && "completed".equalsIgnoreCase(finalState)) {
				finalState = "unstarted";
			}
		}

		return MatchResultDto.builder()
				.matchTitle(stageName + " | " + teamA.path("code").asText() + " vs " + teamB.path("code").asText()) // "결승
																													// |
																													// T1
																													// vs
																													// GEN"
				.matchDate(matchDate)
				.state(finalState)
				.score(winsA + " : " + winsB)
				.blueTeam(MatchResultDto.TeamInfo.builder().code(teamA.path("code").asText())
						.name(teamA.path("name").asText()).wins(winsA).build())
				.redTeam(MatchResultDto.TeamInfo.builder().code(teamB.path("code").asText())
						.name(teamB.path("name").asText()).wins(winsB).build())
				.sets(setVods)
				.build();
	}

	private String findBestVodUrl(JsonNode vodsNode) {
		if (vodsNode.isMissingNode() || !vodsNode.isArray())
			return "";

		String koYoutube = null;
		String koAfreeca = null;
		String enYoutube = null;
		String anyYoutube = null;

		for (JsonNode vod : vodsNode) {
			String locale = vod.path("locale").asText();
			String provider = vod.path("provider").asText();
			String parameter = vod.path("parameter").asText();
			long startMillis = vod.path("startMillis").asLong(0);

			if ("youtube".equalsIgnoreCase(provider)) {
				if ("ko-KR".equalsIgnoreCase(locale)) {
					// 1순위: 한국어 YouTube (즉시 반환)
					koYoutube = buildYoutubeUrl(parameter, startMillis);
				} else if (locale.startsWith("en")) {
					// 3순위: 영어권 YouTube
					if (enYoutube == null) {
						enYoutube = buildYoutubeUrl(parameter, startMillis);
					}
				} else {
					// 4순위: 아무 YouTube
					if (anyYoutube == null) {
						anyYoutube = buildYoutubeUrl(parameter, startMillis);
					}
				}
			} else if ("afreecatv".equalsIgnoreCase(provider) && "ko-KR".equalsIgnoreCase(locale)) {
				// 2순위: 한국어 아프리카TV
				if (koAfreeca == null) {
					koAfreeca = buildAfreecaUrl(parameter, startMillis);
				}
			}
		}

		// 우선순위: ko-KR YouTube > ko-KR Afreeca > English YouTube > Any YouTube
		if (koYoutube != null)
			return koYoutube;
		if (koAfreeca != null)
			return koAfreeca;
		if (enYoutube != null)
			return enYoutube;
		if (anyYoutube != null)
			return anyYoutube;
		return "";
	}

	private String buildYoutubeUrl(String parameter, long startMillis) {
		long seconds = startMillis / 1000;
		return "https://www.youtube.com/watch?v=" + parameter + "&t=" + seconds + "s";
	}

	private String buildAfreecaUrl(String parameter, long startMillis) {
		String url = "https://vod.afreecatv.com/player/" + parameter;
		if (startMillis > 0) {
			long seconds = startMillis / 1000;
			url += "?change_second=" + seconds;
		}
		return url;
	}

	/**
	 * 진행중 경기 라이브 스트림 URL 추출
	 * 우선순위: ko-KR > en-* > 아무거나
	 */
	private String findBestLiveStreamUrl(JsonNode streamsNode) {
		if (streamsNode.isMissingNode() || !streamsNode.isArray())
			return "";

		String koStream = null;
		String enStream = null;
		String anyStream = null;

		for (JsonNode stream : streamsNode) {
			String locale = stream.path("locale").asText();
			String provider = stream.path("provider").asText();
			String parameter = stream.path("parameter").asText();

			String url = buildLiveStreamUrl(provider, parameter);
			if (url.isEmpty())
				continue;

			if ("ko-KR".equalsIgnoreCase(locale)) {
				koStream = url;
			} else if (locale.startsWith("en") && enStream == null) {
				enStream = url;
			} else if (anyStream == null) {
				anyStream = url;
			}
		}

		if (koStream != null)
			return koStream;
		if (enStream != null)
			return enStream;
		if (anyStream != null)
			return anyStream;
		return "";
	}

	private String buildLiveStreamUrl(String provider, String parameter) {
		if ("twitch".equalsIgnoreCase(provider)) {
			return "https://www.twitch.tv/" + parameter;
		} else if ("afreecatv".equalsIgnoreCase(provider)) {
			return "https://play.sooplive.co.kr/" + parameter;
		} else if ("youtube".equalsIgnoreCase(provider)) {
			return "https://www.youtube.com/watch?v=" + parameter;
		}
		return "";
	}

	private JsonNode callScheduleApi(String pageToken, String leagueId) {
		return webClient.get()
				.uri(uriBuilder -> {
					uriBuilder
							.scheme("https")
							.host("esports-api.lolesports.com")
							.path("/persisted/gw/getSchedule")
							.queryParam("hl", "ko-KR")
							.queryParam("leagueId", leagueId);

					// 토큰이 있을 때만 파라미터 추가
					if (pageToken != null && !pageToken.isEmpty()) {
						uriBuilder.queryParam("pageToken", pageToken);
					}

					return uriBuilder.build();
				})
				.header("x-api-key", RIOT_API_KEY)
				.header("Referer", "https://lolesports.com/")
				.retrieve()
				.bodyToMono(JsonNode.class)
				.block();
	}

	private JsonNode callApi(String path, String queryParam, String queryValue) {
		try {
			return webClient.get()
					.uri(uri -> uri
							.scheme("https")
							.host("esports-api.lolesports.com")
							.path(path)
							.queryParam("hl", "ko-KR")
							.queryParam(queryParam, queryValue)
							.build())
					.header("x-api-key", RIOT_API_KEY)
					.header("Referer", "https://lolesports.com/")
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
		} catch (Exception e) {
			log.error("API Call Failed: {}", path, e);
			return null;
		}
	}
}
