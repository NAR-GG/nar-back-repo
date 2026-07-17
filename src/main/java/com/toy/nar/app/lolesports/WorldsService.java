package com.toy.nar.app.lolesports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import com.fasterxml.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorldsService {

	private static final int EVENT_DETAIL_CONCURRENCY_LIMIT = 5;

	private final WebClient webClient;
	@Qualifier("applicationTaskExecutor")
	private final Executor applicationTaskExecutor;
	private final com.toy.nar.app.lolesports.stream.ChzzkLiveChannelResolver chzzkLiveChannelResolver;

	private static final java.util.Map<String, String> LEAGUE_IDS = LeagueConstants.LEAGUE_IDS;

	@Value("${lolesports.riot-api.key}")
	private String RIOT_API_KEY;

	public MatchResponseWrapper getWorldsMatches(String pageToken, String leagueSlug) {
		String normalizedLeague = normalizeLeagueSlug(leagueSlug);
		String leagueId = LEAGUE_IDS.get(normalizedLeague);
		if (leagueId == null) {
			log.warn("Unsupported league slug for getWorldsMatches: {}", leagueSlug);
			return MatchResponseWrapper.builder().matches(List.of()).nextPageToken(null).build();
		}

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

		Semaphore eventDetailPermits = new Semaphore(EVENT_DETAIL_CONCURRENCY_LIMIT);
		List<CompletableFuture<MatchResultDto>> detailFutures = targetEvents.stream()
				.map(event -> {
					String matchId = event.path("match").path("id").asText();
					String startTime = event.path("startTime").asText();
					String blockName = event.path("blockName").asText();
					String state = event.path("state").asText("unstarted"); // 상태 추출

					return CompletableFuture.supplyAsync(
							() -> fetchMatchDetailsWithPermit(eventDetailPermits, matchId, startTime, blockName, state),
							applicationTaskExecutor);
				})
				.toList();

		List<MatchResultDto> matchResults = detailFutures.stream()
				.map(CompletableFuture::join)
				.filter(match -> match != null)
				.sorted((a, b) -> b.getMatchDate().compareTo(a.getMatchDate()))
				.toList();

		return MatchResponseWrapper.builder()
				.matches(matchResults)
				.nextPageToken(nextToken)
				.build();
	}

	private MatchResultDto fetchMatchDetailsWithPermit(Semaphore permits, String eventId, String matchDate,
			String stageName, String matchState) {
		boolean acquired = false;
		try {
			permits.acquire();
			acquired = true;
			return fetchMatchDetailsForSchedule(eventId, matchDate, stageName, matchState);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("상세 조회 대기 중 인터럽트 발생 (ID: {})", eventId, e);
			return null;
		} finally {
			if (acquired) {
				permits.release();
			}
		}
	}

	private MatchResultDto fetchMatchDetailsForSchedule(String eventId, String matchDate,
			String stageName, String matchState) {
		try {
			JsonNode root = callEventDetailsApi(eventId);
			if (root == null) {
				return null;
			}

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

			// [보정 3] 상태가 completed인데, 진행 중인 게임(inProgress)이 있다면 inProgress로 정정
			boolean hasInProgressGame = false;
			if (games.isArray()) {
				for (JsonNode game : games) {
					if ("inProgress".equalsIgnoreCase(game.path("state").asText())) {
						hasInProgressGame = true;
						break;
					}
				}
			}
			if (hasInProgressGame) {
				finalState = "inProgress";
			}

			String liveStreamUrl = findBestLiveStreamUrl(event.path("streams"));
			if ((liveStreamUrl == null || liveStreamUrl.isEmpty()) && "inProgress".equalsIgnoreCase(finalState)) {
				String leagueSlug = LeagueConstants.fromApiSlug(event.path("league").path("slug").asText(""));
				// 동시 진행 리그(EWC A~F, LCK 동시 편성)는 방제 매칭으로 이 경기를 트는 채널을 찾고,
				// 못 찾으면 기존 리그 기본 링크로 폴백한다.
				liveStreamUrl = chzzkLiveChannelResolver.resolve(leagueSlug,
								teamA.path("code").asText(null), teamA.path("name").asText(null),
								teamB.path("code").asText(null), teamB.path("name").asText(null))
						.orElseGet(() -> LeagueConstants.getLiveStreamUrl(leagueSlug));
			}
			return MatchResultDto.builder()
					.matchId(eventId)
					.leagueName(LeagueConstants.fromApiSlug(event.path("league").path("slug").asText("")))
					.matchTitle(stageName + " | " + teamA.path("code").asText() + " vs "
							+ teamB.path("code").asText())
					.matchDate(matchDate)
					.state(finalState)
					.score(winsA + " : " + winsB)
					.blueTeam(MatchResultDto.TeamInfo.builder()
							.externalTeamId(teamA.path("id").asText(null))
							.code(teamA.path("code").asText())
							.name(teamA.path("name").asText())
							.imageUrl(imageA)
							.wins(winsA).build())
					.redTeam(MatchResultDto.TeamInfo.builder()
							.externalTeamId(teamB.path("id").asText(null))
							.code(teamB.path("code").asText())
							.name(teamB.path("name").asText())
							.imageUrl(imageB)
							.wins(winsB).build())
					.sets(setVods)
					.liveGameIds(extractInProgressGameIds(games))
					.gameIds(extractAllGameIds(games))
					.liveStreamUrl(liveStreamUrl)
					.build();
		} catch (Exception e) {
			log.error("상세 조회 실패 (ID: {}): {}", eventId, e.getMessage());
			return null;
		}
	}

	public List<MatchResultDto> getRecent3Matches(String leagueSlug) {
		String normalizedLeague = normalizeLeagueSlug(leagueSlug);
		String leagueId = LEAGUE_IDS.get(normalizedLeague);
		if (leagueId == null) {
			log.warn("Unsupported league slug for getRecent3Matches: {}", leagueSlug);
			return List.of();
		}
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

		// [보정 3] 상태가 completed인데, 진행 중인 게임(inProgress)이 있다면 inProgress로 정정
		boolean hasInProgressGame = false;
		if (games.isArray()) {
			for (JsonNode game : games) {
				if ("inProgress".equalsIgnoreCase(game.path("state").asText())) {
					hasInProgressGame = true;
					break;
				}
			}
		}
		if (hasInProgressGame) {
			finalState = "inProgress";
		}

		String liveStreamUrl = findBestLiveStreamUrl(event.path("streams"));
		// inProgress 상태인데 라이브 스트림 URL이 없으면: 방제 매칭 → 리그별 기본값 순으로 폴백
		if ((liveStreamUrl == null || liveStreamUrl.isEmpty()) && "inProgress".equalsIgnoreCase(finalState)) {
			String leagueSlug = LeagueConstants.fromApiSlug(event.path("league").path("slug").asText(""));
			liveStreamUrl = chzzkLiveChannelResolver.resolve(leagueSlug,
							teamA.path("code").asText(null), teamA.path("name").asText(null),
							teamB.path("code").asText(null), teamB.path("name").asText(null))
					.orElseGet(() -> LeagueConstants.getLiveStreamUrl(leagueSlug));
		}
		return MatchResultDto.builder()
				.matchId(eventId)
				.leagueName(LeagueConstants.fromApiSlug(event.path("league").path("slug").asText("")))
				.matchTitle(teamA.path("code").asText() + " vs " + teamB.path("code").asText())
				.matchDate(matchDate)
				.state(finalState)
				.score(winsA + " : " + winsB)
				.blueTeam(MatchResultDto.TeamInfo.builder()
						.externalTeamId(teamA.path("id").asText(null))
						.code(teamA.path("code").asText())
						.name(teamA.path("name").asText())
						.imageUrl(imageA)
						.wins(winsA)
						.build())
				.redTeam(MatchResultDto.TeamInfo.builder()
						.externalTeamId(teamB.path("id").asText(null))
						.code(teamB.path("code").asText())
						.name(teamB.path("name").asText())
						.imageUrl(imageB)
						.wins(winsB)
						.build())
				.sets(setVods)
				.liveGameIds(extractInProgressGameIds(games))
				.gameIds(extractAllGameIds(games))
				.liveStreamUrl(liveStreamUrl)
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

		String liveStreamUrl = findBestLiveStreamUrl(event.path("streams"));
		// inProgress 상태인데 라이브 스트림 URL이 없으면: 방제 매칭 → 리그별 기본값 순으로 폴백
		if ((liveStreamUrl == null || liveStreamUrl.isEmpty()) && "inProgress".equalsIgnoreCase(finalState)) {
			String leagueSlug = LeagueConstants.fromApiSlug(event.path("league").path("slug").asText(""));
			liveStreamUrl = chzzkLiveChannelResolver.resolve(leagueSlug,
							teamA.path("code").asText(null), teamA.path("name").asText(null),
							teamB.path("code").asText(null), teamB.path("name").asText(null))
					.orElseGet(() -> LeagueConstants.getLiveStreamUrl(leagueSlug));
		}
		return MatchResultDto.builder()
				.matchId(eventId)
				.leagueName(LeagueConstants.fromApiSlug(event.path("league").path("slug").asText("")))
				.matchTitle(stageName + " | " + teamA.path("code").asText() + " vs " + teamB.path("code").asText())
				.matchDate(matchDate)
				.state(finalState)
				.score(winsA + " : " + winsB)
				.blueTeam(MatchResultDto.TeamInfo.builder().code(teamA.path("code").asText())
						.externalTeamId(teamA.path("id").asText(null))
						.name(teamA.path("name").asText()).wins(winsA).build())
				.redTeam(MatchResultDto.TeamInfo.builder().code(teamB.path("code").asText())
						.externalTeamId(teamB.path("id").asText(null))
						.name(teamB.path("name").asText()).wins(winsB).build())
				.sets(setVods)
				.liveGameIds(extractInProgressGameIds(games))
				.gameIds(extractAllGameIds(games))
				.liveStreamUrl(liveStreamUrl)
				.build();
	}

	private String normalizeLeagueSlug(String leagueSlug) {
		// fromApiSlug 로 슬러그 변형(EWC: ewc_lol)까지 내부 리그명으로 보정 — 그래야 LEAGUE_IDS 해석이 실패하지 않는다.
		return leagueSlug == null || leagueSlug.isBlank() ? "LCK" : LeagueConstants.fromApiSlug(leagueSlug);
	}

	private List<String> extractInProgressGameIds(JsonNode games) {
		List<String> ids = new ArrayList<>();
		if (!games.isArray()) {
			return ids;
		}
		for (JsonNode game : games) {
			if (!"inProgress".equalsIgnoreCase(game.path("state").asText())) {
				continue;
			}
			String gameId = game.path("id").asText();
			if (gameId != null && !gameId.isBlank()) {
				ids.add(gameId);
			}
		}
		return ids;
	}

	private List<String> extractAllGameIds(JsonNode games) {
		List<String> ids = new ArrayList<>();
		if (!games.isArray()) {
			return ids;
		}
		for (JsonNode game : games) {
			String gameId = game.path("id").asText();
			if (gameId != null && !gameId.isBlank()) {
				ids.add(gameId);
			}
		}
		return ids;
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

	private JsonNode callEventDetailsApi(String eventId) {
		return webClient.get()
				.uri(uri -> uri
						.scheme("https")
						.host("esports-api.lolesports.com")
						.path("/persisted/gw/getEventDetails")
						.queryParam("hl", "ko-KR")
						.queryParam("id", eventId)
						.build())
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

	private List<String> extractTrackedGameIds(JsonNode games) {
		List<String> ids = new ArrayList<>();
		if (!games.isArray()) {
			return ids;
		}
		for (JsonNode game : games) {
			String state = game.path("state").asText();
			if (!"completed".equalsIgnoreCase(state) && !"inProgress".equalsIgnoreCase(state)) {
				continue;
			}
			String gameId = game.path("id").asText();
			if (gameId != null && !gameId.isBlank()) {
				ids.add(gameId);
			}
		}
		return ids;
	}

	public List<String> getGameIdsByMatchId(String matchId) {
		if (matchId == null || matchId.isBlank()) {
			return List.of();
		}
		JsonNode root = callApi("/persisted/gw/getEventDetails", "id", matchId);
		if (root == null) {
			return List.of();
		}
		JsonNode games = root.path("data").path("event").path("match").path("games");
		return extractTrackedGameIds(games);
	}
}
