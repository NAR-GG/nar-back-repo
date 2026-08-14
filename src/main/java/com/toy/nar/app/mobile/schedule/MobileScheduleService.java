package com.toy.nar.app.mobile.schedule;

import com.toy.nar.app.lolesports.LeagueConstants;
import com.toy.nar.app.lolesports.live.ActiveLiveGame;
import com.toy.nar.app.lolesports.live.LiveStateStore;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchGamesResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileMatchPageResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleCalendarResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleFilterResponse;
import com.toy.nar.app.mobile.schedule.dto.MobileScheduleListResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.common.util.NameNormalizer;
import com.toy.nar.domain.participant.entity.Team;
import com.toy.nar.domain.participant.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileScheduleService {

	private static final String DEFAULT_LEAGUE = "LCK";
	private static final String ALL_LEAGUES = "ALL";
	private static final int DEFAULT_TEAM_YEAR = 2026;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
	private static final List<String> LCK_TEAM_CODES = List.of(
			"T1", "HLE", "GEN", "DK", "KT",
			"DNS", "BFX", "NS", "BRO", "KRX"
	);
	private static final String LOLESPORTS_SOURCE = "LOLESPORTS";
	/** 세트 상태 — 종료(라이브 데이터 보유). 승리 팀은 이 상태에서만 노출한다. */
	private static final String STATUS_ENDED = "ENDED";
	private static final String CURSOR_DELIMITER = "|";
	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 50;
	/**
	 * {@code around} 과거쪽 경계 커서의 matchId 자리.
	 *
	 * <p>커서 조건이 {@code matchDate < :cursorDate OR (matchDate = :cursorDate AND id < :cursorId)} 라
	 * cursorId 가 null 이면 커서 조건 전체가 무시된다. 앵커 시각 "이전"만 뽑으려면 값이 필요하고,
	 * 실제 matchDate 가 앵커와 같은 행은 미래쪽이 담아야 하므로 <b>어떤 matchId 보다도 작은</b> 값을 준다.
	 * league_match.id 는 전부 18자리 숫자 문자열이고 최소값이 {@code 109466537707382618} 이라
	 * 문자열 비교에서 {@code "0"} 보다 항상 크다(2026-08-14 프로덕션 확인).</p>
	 */
	private static final String PAST_BOUNDARY_MATCH_ID = "0";
	private static final com.fasterxml.jackson.databind.ObjectMapper VOD_MAPPER =
			new com.fasterxml.jackson.databind.ObjectMapper();

	private final LeagueMatchRepository leagueMatchRepository;
	private final LeagueMatchGameRepository leagueMatchGameRepository;
	private final TeamRepository teamRepository;
	private final LiveStateStore liveStateStore;
	private final LiveGameMinuteSnapshotRepository minuteSnapshotRepository;

	public MobileScheduleFilterResponse getFilters(String league) {
		String normalizedLeague = normalizeLeague(league);
		List<MobileScheduleFilterResponse.LeagueOption> leagues = new ArrayList<>();
		leagues.add(new MobileScheduleFilterResponse.LeagueOption(ALL_LEAGUES, "전체"));
		LeagueConstants.ALLOWED_LEAGUES.stream()
				.sorted()
				.forEach(code -> leagues.add(new MobileScheduleFilterResponse.LeagueOption(code, code)));
		// 전체 리그 선택 시 팀 필터는 모든 리그 팀의 합집합을 노출한다.
		List<MobileScheduleFilterResponse.TeamOption> teams = findFilterTeams(normalizedLeague).stream()
				.map(this::toTeamOption)
				.toList();
		List<MobileScheduleFilterResponse.SeasonOption> seasons = leagueMatchRepository
				.findSeasonOptions(leagueParam(normalizedLeague)).stream()
				.map(row -> new MobileScheduleFilterResponse.SeasonOption(
						row.getSeasonYear(),
						row.getSeasonSplit(),
						row.getSeasonYear() + " " + row.getSeasonSplit()))
				.toList();

		return new MobileScheduleFilterResponse(DEFAULT_LEAGUE, leagues, teams, seasons);
	}

	public MobileScheduleCalendarResponse getCalendar(YearMonth month, List<String> league, List<Long> teamId) {
		if (month == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		List<String> normalizedLeagues = normalizeLeagues(league);
		TeamFilters teamFilters = resolveTeamFilters(teamId);
		LocalDateTime startUtc = toUtc(month.atDay(1).atStartOfDay());
		LocalDateTime endUtc = toUtc(month.plusMonths(1).atDay(1).atStartOfDay());
		List<LeagueMatch> matches = findMatches(normalizedLeagues, teamFilters, startUtc, endUtc);

		Map<String, MobileScheduleCalendarResponse.DateSummary> summaries = new LinkedHashMap<>();
		for (LeagueMatch match : matches) {
			String date = toKstDate(match).toString();
			MobileScheduleCalendarResponse.DateSummary existing = summaries.get(date);
			if (existing == null) {
				summaries.put(date, new MobileScheduleCalendarResponse.DateSummary(
						date,
						1,
						new ArrayList<>(List.of(toCalendarMatch(match)))));
				continue;
			}
			List<MobileScheduleCalendarResponse.CalendarMatch> dayMatches = new ArrayList<>(existing.matches());
			dayMatches.add(toCalendarMatch(match));
			summaries.put(date, new MobileScheduleCalendarResponse.DateSummary(
					date,
					existing.matchCount() + 1,
					dayMatches));
		}

		return new MobileScheduleCalendarResponse(
				month.toString(),
				echoLeague(normalizedLeagues),
				echoTeamId(teamId),
				new ArrayList<>(summaries.values()));
	}

	public MobileScheduleListResponse getDailySchedules(LocalDate date, List<String> league, List<Long> teamId) {
		if (date == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		List<String> normalizedLeagues = normalizeLeagues(league);
		TeamFilters teamFilters = resolveTeamFilters(teamId);
		LocalDateTime startUtc = toUtc(date.atStartOfDay());
		LocalDateTime endUtc = toUtc(date.plusDays(1).atStartOfDay());
		List<LeagueMatch> found = findMatches(normalizedLeagues, teamFilters, startUtc, endUtc);
		Map<String, List<MobileScheduleListResponse.MobileGameSummary>> gamesByMatchId = loadGames(found);
		List<MobileScheduleListResponse.MobileMatchSummary> matches = found.stream()
				.map(match -> toMatchSummary(match, gamesByMatchId))
				.toList();

		return new MobileScheduleListResponse(date.toString(), echoLeague(normalizedLeagues), echoTeamId(teamId), matches);
	}

	/**
	 * 경기 리스트 커서 페이지 (기존 시그니처).
	 *
	 * <p>ponytail: {@code around}/{@code before} 없는 호출이 대부분이라 위임 오버로드로 남긴다.</p>
	 */
	public MobileMatchPageResponse getMatchPage(
			String league,
			Long teamId,
			Integer seasonYear,
			String seasonSplit,
			String cursor,
			Integer size,
			LocalDate from) {
		return getMatchPage(league, teamId, seasonYear, seasonSplit, cursor, size, from, null, null);
	}

	/**
	 * 경기 리스트 커서 페이지.
	 *
	 * <p>진입 방식이 셋이고 서로 배타적이다.</p>
	 * <ul>
	 *   <li>{@code around}: 그 날짜(KST 00:00)를 기준으로 <b>과거 절반 + 미래 절반</b>을 한 번에 내린다.
	 *       "오늘로 진입" 용도 — 앱이 시즌 첫 경기부터 오늘까지 페이지를 당기지 않아도 된다.</li>
	 *   <li>{@code before}: 그 커서보다 <b>과거</b>를 내린다(위로 스크롤). 결과는 과거→미래 순이라
	 *       호출자가 기존 목록 앞에 그대로 붙일 수 있다.</li>
	 *   <li>{@code cursor}(+{@code from}): 기존 경로. {@code from} 이 있으면 오름차순, 없으면 내림차순.</li>
	 * </ul>
	 *
	 * <p>{@code around} 로 시작한 뒤 미래 방향으로 더 받을 때는 {@code from=around 날짜} 를 유지한 채
	 * {@code cursor=nextCursor} 를 보내면 같은 오름차순 축에서 이어진다 — 그래서 미래 방향 전용
	 * 파라미터를 따로 두지 않았다.</p>
	 *
	 * <p>커서 값은 (matchDate, id) 그대로라 방향을 담지 않는다. 방향은 어떤 파라미터로 보냈는지가 정한다.</p>
	 *
	 * <p><b>정렬 파라미터(sort)를 받지 않는 건 의도된 선택이다.</b> 앱의 정렬 3개 중 서버 조건이
	 * 바뀌는 건 '오늘 이후'({@code from}) 하나뿐이다 — 최근순과 오래된 순은 서버에 같은 요청을 보내고
	 * {@code ListView.reverse} 로 표시 방향만 뒤집는다(2026-08-14 앱 코드 확인). 그리고 세 정렬 모두
	 * 오늘로 스크롤하므로 "오늘 중심"이 확정된 UX고, 정렬은 표시 선택이다. 서버 파라미터는 데이터
	 * 범위({@code from})나 진입 지점({@code around})이 바뀔 때만 필요하다.</p>
	 *
	 * <p>역으로 sort=ASC 를 서버에서 지원하면 오래된 순만 시즌 첫 경기부터 받게 되어 오늘까지
	 * 16페이지를 당겨야 한다(2026-08 시즌 ALL, size=50 기준). sort 는 문제를 만드는 쪽이다.</p>
	 */
	public MobileMatchPageResponse getMatchPage(
			String league,
			Long teamId,
			Integer seasonYear,
			String seasonSplit,
			String cursor,
			Integer size,
			LocalDate from,
			LocalDate around,
			String before) {
		boolean hasAround = around != null;
		boolean hasBefore = before != null && !before.isBlank();
		boolean hasCursor = cursor != null && !cursor.isBlank();
		// 진입 방식이 섞이면 어느 방향인지 서버가 임의로 골라야 한다 — 조용히 고르지 않고 거절한다.
		if ((hasAround && (hasBefore || hasCursor)) || (hasBefore && hasCursor)) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}

		String normalizedLeague = normalizeLeague(league);
		String leagueParam = leagueParam(normalizedLeague);
		TeamFilter teamFilter = resolveTeamFilter(teamId);
		String normalizedSplit = seasonSplit == null || seasonSplit.isBlank() ? null : seasonSplit.trim();
		int pageSize = size == null
				? DEFAULT_PAGE_SIZE
				: Math.max(1, Math.min(size, MAX_PAGE_SIZE));

		if (hasAround) {
			return matchPageAround(
					normalizedLeague, leagueParam, teamId, teamFilter, seasonYear, normalizedSplit, pageSize, around);
		}
		if (hasBefore) {
			return matchPageBefore(
					normalizedLeague, leagueParam, teamId, teamFilter, seasonYear, normalizedSplit, pageSize, before);
		}

		MatchCursor matchCursor = decodeCursor(cursor);
		LocalDateTime cursorDate = matchCursor != null ? matchCursor.matchDate() : null;
		String cursorId = matchCursor != null ? matchCursor.matchId() : null;
		// matchDate 는 UTC 저장이라 KST 기준 그날 00:00 을 UTC 로 옮겨 비교한다.
		LocalDateTime fromUtc = from == null ? null : toUtc(from.atStartOfDay());

		List<LeagueMatch> fetched = fromUtc == null
				? fetchDesc(leagueParam, teamFilter, seasonYear, normalizedSplit, cursorDate, cursorId, pageSize + 1)
				: fetchAsc(leagueParam, teamFilter, seasonYear, normalizedSplit, fromUtc, cursorDate, cursorId,
						pageSize + 1);

		List<LeagueMatch> page = fetched.size() > pageSize ? fetched.subList(0, pageSize) : fetched;
		String nextCursor = fetched.size() > pageSize ? encodeCursor(page.getLast()) : null;

		return MobileMatchPageResponse.forward(normalizedLeague, teamId, toMatchSummaries(page), nextCursor);
	}

	/**
	 * {@code around} 진입 — 앵커 날짜(KST 00:00) 기준으로 과거 절반과 미래 절반을 각각 뽑아 이어 붙인다.
	 *
	 * <p>홀수는 미래쪽에 한 자리를 더 준다. 앵커가 "오늘"인 진입에서는 앞으로 볼 경기가 지난 경기보다
	 * 중요하다.</p>
	 *
	 * <p>앵커 정각(00:00)에 시작하는 경기는 미래쪽에 포함된다 — 과거쪽 경계 커서의 id 가
	 * {@link #PAST_BOUNDARY_MATCH_ID} 라 {@code matchDate = 앵커} 인 행은 과거 조건에 걸리지 않고,
	 * 미래쪽은 {@code matchDate >= 앵커} 라 그대로 담긴다. 양쪽에 중복되거나 빠지는 행이 없다.</p>
	 */
	private MobileMatchPageResponse matchPageAround(
			String normalizedLeague,
			String leagueParam,
			Long teamId,
			TeamFilter teamFilter,
			Integer seasonYear,
			String seasonSplit,
			int pageSize,
			LocalDate around) {
		LocalDateTime anchorUtc = toUtc(around.atStartOfDay());
		int futureSize = pageSize / 2 + pageSize % 2;
		int pastSize = pageSize - futureSize;

		// 과거쪽: 앵커 이전을 내림차순으로 받아 뒤집는다(응답은 과거→미래 한 줄).
		List<LeagueMatch> pastDesc = pastSize == 0
				? List.of()
				: fetchDesc(leagueParam, teamFilter, seasonYear, seasonSplit,
						anchorUtc, PAST_BOUNDARY_MATCH_ID, pastSize + 1);
		boolean hasPrev = pastDesc.size() > pastSize;
		List<LeagueMatch> past = new ArrayList<>(hasPrev ? pastDesc.subList(0, pastSize) : pastDesc);
		Collections.reverse(past);

		List<LeagueMatch> futureAsc = fetchAsc(leagueParam, teamFilter, seasonYear, seasonSplit,
				anchorUtc, null, null, futureSize + 1);
		boolean hasNext = futureAsc.size() > futureSize;
		List<LeagueMatch> future = hasNext ? futureAsc.subList(0, futureSize) : futureAsc;

		List<LeagueMatch> page = new ArrayList<>(past);
		page.addAll(future);

		return new MobileMatchPageResponse(
				normalizedLeague,
				teamId,
				toMatchSummaries(page),
				hasNext && !page.isEmpty() ? encodeCursor(page.getLast()) : null,
				hasNext,
				hasPrev && !page.isEmpty() ? encodeCursor(page.getFirst()) : null,
				hasPrev);
	}

	/**
	 * {@code before} 진입 — 커서보다 과거를 내림차순으로 받아 뒤집어 과거→미래 순으로 내린다.
	 *
	 * <p>미래쪽({@code nextCursor}/{@code hasNext})은 호출자가 이미 들고 있는 구간이다. 값은 채워 주지만
	 * 그대로 되던지면 이미 받은 페이지를 다시 받게 되므로, 위로 스크롤할 때는 {@code prevCursor} 만 쓴다.</p>
	 */
	private MobileMatchPageResponse matchPageBefore(
			String normalizedLeague,
			String leagueParam,
			Long teamId,
			TeamFilter teamFilter,
			Integer seasonYear,
			String seasonSplit,
			int pageSize,
			String before) {
		MatchCursor cursor = decodeCursor(before);
		List<LeagueMatch> fetched = fetchDesc(leagueParam, teamFilter, seasonYear, seasonSplit,
				cursor.matchDate(), cursor.matchId(), pageSize + 1);
		boolean hasPrev = fetched.size() > pageSize;
		List<LeagueMatch> page = new ArrayList<>(hasPrev ? fetched.subList(0, pageSize) : fetched);
		Collections.reverse(page);

		return new MobileMatchPageResponse(
				normalizedLeague,
				teamId,
				toMatchSummaries(page),
				page.isEmpty() ? null : encodeCursor(page.getLast()),
				!page.isEmpty(),
				hasPrev ? encodeCursor(page.getFirst()) : null,
				hasPrev);
	}

	private List<LeagueMatch> fetchDesc(
			String leagueParam,
			TeamFilter teamFilter,
			Integer seasonYear,
			String seasonSplit,
			LocalDateTime cursorDate,
			String cursorId,
			int limit) {
		PageRequest fetchLimit = PageRequest.of(0, limit);
		return teamFilter == null
				? leagueMatchRepository.findMobileMatchPage(
						leagueParam, seasonYear, seasonSplit, cursorDate, cursorId, fetchLimit)
				: leagueMatchRepository.findMobileTeamMatchPage(
						leagueParam, teamFilter.name(), teamFilter.code(), seasonYear, seasonSplit,
						cursorDate, cursorId, fetchLimit);
	}

	private List<LeagueMatch> fetchAsc(
			String leagueParam,
			TeamFilter teamFilter,
			Integer seasonYear,
			String seasonSplit,
			LocalDateTime fromUtc,
			LocalDateTime cursorDate,
			String cursorId,
			int limit) {
		PageRequest fetchLimit = PageRequest.of(0, limit);
		return teamFilter == null
				? leagueMatchRepository.findMobileMatchPageAsc(
						leagueParam, seasonYear, seasonSplit, fromUtc, cursorDate, cursorId, fetchLimit)
				: leagueMatchRepository.findMobileTeamMatchPageAsc(
						leagueParam, teamFilter.name(), teamFilter.code(), seasonYear, seasonSplit,
						fromUtc, cursorDate, cursorId, fetchLimit);
	}

	private List<MobileScheduleListResponse.MobileMatchSummary> toMatchSummaries(List<LeagueMatch> page) {
		Map<String, List<MobileScheduleListResponse.MobileGameSummary>> gamesByMatchId = loadGames(page);
		return page.stream()
				.map(match -> toMatchSummary(match, gamesByMatchId))
				.toList();
	}

	/**
	 * 매치 단건 조회. 푸시 알림 딥링크처럼 matchId 만 알고 진입하는 화면이
	 * 리스트 응답과 동일한 형태(MobileMatchSummary)로 경기 정보를 채울 때 쓴다.
	 */
	public MobileScheduleListResponse.MobileMatchSummary getMatch(String matchId) {
		LeagueMatch match = leagueMatchRepository.findById(matchId)
				.orElseThrow(() -> new CustomException(ErrorCode.DATA_NOT_FOUND));
		return toMatchSummary(match, loadGames(List.of(match)));
	}

	public MobileMatchGamesResponse getMatchGames(String matchId) {
		LeagueMatch match = leagueMatchRepository.findById(matchId)
				.orElseThrow(() -> new CustomException(ErrorCode.DATA_NOT_FOUND));
		// 세트별 다시보기 VOD URL (setNumber == gameOrder 기준). 없으면 빈 맵.
		Map<Integer, String> vodMap = parseVodMap(match.getMatchDetailsJson());
		// 상태 판정용 집합: 현재 라이브(스토어) / 라이브 데이터 수집됨(영속 스냅샷, 종료 후에도 유지).
		// 프레임 finished 로 종료 확정된 게임은 stale 확정(3분)까지 store 에 남는 잔상이므로
		// LIVE 에서 제외한다 — SET_END 푸시와 세트 상태가 같은 신호(프레임)를 보게 된다.
		java.util.Set<String> liveGameIds = liveStateStore.getActiveGames().values().stream()
				.filter(live -> match.getId().equals(live.matchId()))
				.map(ActiveLiveGame::gameId)
				.filter(gameId -> gameId != null && !gameId.isBlank())
				.filter(gameId -> !liveStateStore.isFinished(gameId))
				.collect(Collectors.toCollection(java.util.LinkedHashSet::new));
		List<String> recordedGameIds = minuteSnapshotRepository.findGameIdsByMatchIdOrderByStart(match.getId());
		java.util.Set<String> recordedSet = new java.util.LinkedHashSet<>(recordedGameIds);

		List<MobileScheduleListResponse.MobileGameSummary> games = new ArrayList<>();
		java.util.Set<String> knownGameIds = new java.util.LinkedHashSet<>();
		int maxOrder = 0;
		// 완료된 매치의 매핑 세트는 전부 실제로 치러진 세트다 — 업스트림 동기화가 unneeded 세트를
		// 애초에 매핑하지 않기 때문이다. 라이브 수집 이전 경기까지 ENDED 로 내려야 세트 승자가 노출된다.
		boolean matchCompleted = "completed".equalsIgnoreCase(match.getState());

		// 1) DB 공식 매핑 게임 (gameOrder, recordGameId 보존) + 상태 계산
		for (LeagueMatchGameRepository.MappedGameWinnerRow row :
				leagueMatchGameRepository.findMappedGameWinnerRowsByMatchId(match.getId(), LOLESPORTS_SOURCE)) {
			String gameId = row.getExternalGameId();
			String status = gameStatus(gameId, liveGameIds, recordedSet,
					matchCompleted || row.getInternalGameId() != null);
			games.add(new MobileScheduleListResponse.MobileGameSummary(
					row.getGameOrder(), gameId, row.getInternalGameId(),
					status,
					row.getGameOrder() != null ? vodMap.get(row.getGameOrder()) : null,
					winnerTeamCode(match, row, row.getGameOrder(), status)));
			knownGameIds.add(gameId);
			if (row.getGameOrder() != null) {
				maxOrder = Math.max(maxOrder, row.getGameOrder());
			}
		}

		// 2) DB 매핑에 없는 세트 보강: 라이브 데이터를 수집한 게임(영속, 시작 시각 순) + 현재 라이브 게임.
		//    데이터도 없고 라이브도 아닌 gameId 는 자연히 제외된다. 게임 종료/서버 재기동 후에도 유지된다.
		java.util.LinkedHashSet<String> extraGameIds = new java.util.LinkedHashSet<>(recordedGameIds);
		extraGameIds.addAll(liveGameIds);
		extraGameIds.removeAll(knownGameIds);
		int nextOrder = maxOrder + 1;
		for (String gameId : extraGameIds) {
			String status = gameStatus(gameId, liveGameIds, recordedSet, matchCompleted);
			int order = nextOrder++;
			games.add(new MobileScheduleListResponse.MobileGameSummary(
					order, gameId, null, status, null,
					winnerTeamCode(match, null, order, status)));
		}

		return new MobileMatchGamesResponse(match.getId(), games);
	}

	/**
	 * 세트 승리 팀 코드. 값은 항상 매치의 blue/red 팀 코드 중 하나이며, 종료된 세트에만 채운다.
	 *
	 * <p>1순위는 적재된 경기 기록(수 일 지연·정합 검증됨), 2순위는 스코어 전이로 적어둔
	 * set_winners(세트 종료 후 첫 sync 에 확정 — 라이브 중에도 채워진다), 3순위는 완봉 산술이다.
	 * 셋 다 안 되면 null.</p>
	 */
	private String winnerTeamCode(
			LeagueMatch match,
			LeagueMatchGameRepository.MappedGameWinnerRow row,
			Integer gameOrder,
			String status) {
		if (!STATUS_ENDED.equals(status)) {
			return null;
		}
		if (row != null) {
			String externalTeamId = row.getWinnerExternalTeamId();
			if (externalTeamId != null) {
				if (externalTeamId.equals(match.getBlueExternalTeamId())) {
					return match.getBlueTeamCode();
				}
				if (externalTeamId.equals(match.getRedExternalTeamId())) {
					return match.getRedTeamCode();
				}
			}
			// 외부 팀 id 매핑이 없을 때만 내부 팀 코드로 대조한다.
			String teamCode = row.getWinnerTeamCode();
			if (teamCode != null) {
				if (teamCode.equalsIgnoreCase(match.getBlueTeamCode())) {
					return match.getBlueTeamCode();
				}
				if (teamCode.equalsIgnoreCase(match.getRedTeamCode())) {
					return match.getRedTeamCode();
				}
			}
		}
		String recorded = recordedSetWinnerTeamCode(match, gameOrder);
		if (recorded != null) {
			return recorded;
		}
		return sweepWinnerTeamCode(match);
	}

	/** 스코어 전이 기록(set_winners)의 gameOrder 번째 승자. 없거나 '?' 면 null. */
	private String recordedSetWinnerTeamCode(LeagueMatch match, Integer gameOrder) {
		String setWinners = match.getSetWinners();
		if (setWinners == null || setWinners.isBlank() || gameOrder == null || gameOrder < 1) {
			return null;
		}
		String[] winners = setWinners.split(",");
		if (gameOrder > winners.length) {
			return null;
		}
		return switch (winners[gameOrder - 1]) {
			case "B" -> match.getBlueTeamCode();
			case "R" -> match.getRedTeamCode();
			default -> null;
		};
	}

	/** 완봉 경기면 이긴 팀 코드, 아니면 null. */
	private String sweepWinnerTeamCode(LeagueMatch match) {
		if (!"completed".equalsIgnoreCase(match.getState())) {
			return null;
		}
		Integer blueScore = match.getBlueScore();
		Integer redScore = match.getRedScore();
		if (blueScore == null || redScore == null) {
			return null;
		}
		if (blueScore > 0 && redScore == 0) {
			return match.getBlueTeamCode();
		}
		if (redScore > 0 && blueScore == 0) {
			return match.getRedTeamCode();
		}
		return null;
	}

	private List<LeagueMatch> findMatches(
			List<String> leagues,
			TeamFilters teamFilters,
			LocalDateTime startUtc,
			LocalDateTime endUtc) {
		List<String> leagueParams = leagueParams(leagues);
		if (teamFilters == null) {
			return leagueMatchRepository.findMobileMatchesInRange(leagueParams, startUtc, endUtc);
		}
		return leagueMatchRepository.findMobileTeamMatchesInRange(
				leagueParams,
				teamFilters.names(),
				teamFilters.codes(),
				startUtc,
				endUtc);
	}

	private MobileScheduleFilterResponse.TeamOption toTeamOption(Team team) {
		return new MobileScheduleFilterResponse.TeamOption(
				team.getId(),
				team.getName(),
				team.getCode(),
				team.getImageUrl());
	}

	private List<Team> findFilterTeams(String league) {
		// LCK 는 큐레이션된 고정 순서를 유지한다.
		if (DEFAULT_LEAGUE.equals(league)) {
			Map<String, Team> teamsByCode = teamRepository.findAllByCodeIn(LCK_TEAM_CODES).stream()
					.collect(Collectors.toMap(Team::getCode, Function.identity(), (left, right) -> left));
			return LCK_TEAM_CODES.stream()
					.map(teamsByCode::get)
					.filter(team -> team != null)
					.toList();
		}

		// 그 외 리그 및 전체(ALL): 현재 시즌 경기 출전팀(이름순). ALL 은 리그 조건 없이 합집합.
		// league_team(역대 누적) 대신 실제 경기 출전 코드를 활성 팀으로 본다.
		java.util.Set<String> codes = new java.util.LinkedHashSet<>();
		for (Object[] pair : leagueMatchRepository.findTeamCodePairsBySeason(leagueParam(league), DEFAULT_TEAM_YEAR)) {
			addCode(codes, (String) pair[0]);
			addCode(codes, (String) pair[1]);
		}
		if (codes.isEmpty()) {
			return List.of();
		}
		return teamRepository.findAllByCodeIn(codes).stream()
				.sorted(Comparator.comparing(Team::getName))
				.toList();
	}

	private void addCode(java.util.Set<String> codes, String code) {
		if (code != null && !code.isBlank()) {
			codes.add(code);
		}
	}

	private MobileScheduleListResponse.MobileMatchSummary toMatchSummary(
			LeagueMatch match,
			Map<String, List<MobileScheduleListResponse.MobileGameSummary>> gamesByMatchId) {
		return new MobileScheduleListResponse.MobileMatchSummary(
				match.getId(),
				match.getMatchDate() != null ? toKstDate(match).toString() : null,
				toScheduledTime(match),
				match.getState(),
				match.getMatchTitle(),
				normalizeLeague(match.getLeagueName()),
				toTeamResult(
						match.getBlueTeamName(),
						match.getBlueTeamCode(),
						match.getBlueTeamImageUrl(),
						match.getBlueScore()),
				toTeamResult(
						match.getRedTeamName(),
						match.getRedTeamCode(),
						match.getRedTeamImageUrl(),
						match.getRedScore()),
				liveStreamUrl(match),
				streamLinks(match),
				match.getBestOf(),
				gamesByMatchId.getOrDefault(match.getId(), List.of()));
	}

	private Map<String, List<MobileScheduleListResponse.MobileGameSummary>> loadGames(List<LeagueMatch> matches) {
		if (matches.isEmpty()) {
			return Map.of();
		}
		List<String> matchIds = matches.stream().map(LeagueMatch::getId).toList();
		return leagueMatchGameRepository.findMappedGameRowsByMatchIds(matchIds, LOLESPORTS_SOURCE).stream()
				.collect(Collectors.groupingBy(
						LeagueMatchGameRepository.MappedGameRow::getMatchId,
						LinkedHashMap::new,
						Collectors.mapping(this::toGameSummary, Collectors.toList())));
	}

	private MobileScheduleListResponse.MobileGameSummary toGameSummary(LeagueMatchGameRepository.MappedGameRow row) {
		// 일정 목록에서는 세트 상태·VOD·승리 팀을 계산하지 않는다(상세 화면에서만 채움).
		return new MobileScheduleListResponse.MobileGameSummary(
				row.getGameOrder(),
				row.getExternalGameId(),
				row.getInternalGameId(),
				null,
				null,
				null);
	}

	// ponytail: ScheduleService.parseVodMap 과 동일 로직 소량 중복. 공유 유틸은 두 호출처뿐이라 보류.
	private Map<Integer, String> parseVodMap(String matchDetailsJson) {
		Map<Integer, String> vodMap = new LinkedHashMap<>();
		if (matchDetailsJson == null || matchDetailsJson.isBlank()) {
			return vodMap;
		}
		try {
			List<com.toy.nar.app.lolesports.MatchResultDto.SetVod> sets = VOD_MAPPER.readValue(
					matchDetailsJson,
					new com.fasterxml.jackson.core.type.TypeReference<>() {
					});
			for (com.toy.nar.app.lolesports.MatchResultDto.SetVod setVod : sets) {
				if (setVod.getVodUrl() != null && !setVod.getVodUrl().isBlank()) {
					vodMap.put(setVod.getSetNumber(), setVod.getVodUrl());
				}
			}
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			// 파싱 실패는 VOD 없음으로 간주.
		}
		return vodMap;
	}

	/**
	 * 게임 상태 판정: 라이브 스토어에 있으면 LIVE, 종료가 확인되면 ENDED, 아니면 SCHEDULED.
	 *
	 * <p>{@code knownFinished} 는 라이브 스냅샷 외의 종료 증거다(완료된 매치의 매핑 세트, 적재된 경기 기록).
	 * 라이브 수집 이전 경기는 스냅샷이 없어 종료된 세트가 SCHEDULED 로 내려갔다 — 전체 매핑 세트
	 * 4,390건 중 스냅샷 보유는 69건뿐이라 세트 승자를 사실상 못 내보냈다.</p>
	 */
	private String gameStatus(
			String gameId,
			java.util.Set<String> liveGameIds,
			java.util.Set<String> recordedGameIds,
			boolean knownFinished) {
		if (liveGameIds.contains(gameId)) {
			return "LIVE";
		}
		if (recordedGameIds.contains(gameId) || knownFinished) {
			return STATUS_ENDED;
		}
		return "SCHEDULED";
	}

	private MatchCursor decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			int delimiterIndex = decoded.lastIndexOf(CURSOR_DELIMITER);
			LocalDateTime matchDate = LocalDateTime.parse(
					decoded.substring(0, delimiterIndex),
					DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			String matchId = decoded.substring(delimiterIndex + 1);
			if (matchId.isBlank()) {
				throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
			}
			return new MatchCursor(matchDate, matchId);
		} catch (CustomException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
	}

	private String encodeCursor(LeagueMatch match) {
		if (match.getMatchDate() == null) {
			// matchDate가 null인 행은 정렬 마지막에 위치해 커서 기준점이 될 수 없다
			return null;
		}
		String raw = match.getMatchDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
				+ CURSOR_DELIMITER
				+ match.getId();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private MobileScheduleCalendarResponse.CalendarMatch toCalendarMatch(LeagueMatch match) {
		String blueTeamCode = match.getBlueTeamCode();
		String redTeamCode = match.getRedTeamCode();
		String blueTeamName = NameNormalizer.normalizeTeamName(match.getBlueTeamName());
		String redTeamName = NameNormalizer.normalizeTeamName(match.getRedTeamName());
		return new MobileScheduleCalendarResponse.CalendarMatch(
				match.getId(),
				blueTeamCode,
				redTeamCode,
				blueTeamName,
				redTeamName,
				displayTeam(blueTeamCode, blueTeamName) + " vs " + displayTeam(redTeamCode, redTeamName));
	}

	private String displayTeam(String teamCode, String teamName) {
		if (teamCode != null && !teamCode.isBlank()) {
			return teamCode;
		}
		return teamName;
	}

	private MobileScheduleListResponse.MobileTeamResult toTeamResult(
			String teamName,
			String teamCode,
			String teamImageUrl,
			Integer score) {
		return new MobileScheduleListResponse.MobileTeamResult(
				NameNormalizer.normalizeTeamName(teamName),
				teamCode,
				teamImageUrl,
				score != null ? score : 0);
	}

	private String liveStreamUrl(LeagueMatch match) {
		// 하위호환: 구버전 앱이 쓰는 단일 링크. streamLinks 의 첫 번째(대표 채널)와 동일하게 유지한다.
		// 링크 없는 리그(KeSPA=Disney+ 독점)는 빈 목록이라 null 을 내려 링크를 노출하지 않는다.
		if ("inProgress".equalsIgnoreCase(match.getState())) {
			List<LeagueConstants.StreamLink> links = LeagueConstants.getStreamLinks(match.getLeagueName());
			return links.isEmpty() ? null : links.get(0).url();
		}
		return null;
	}

	/**
	 * 라이브 중계 채널 목록. 진행 중 경기에서만 채워지고, 복수면 앱이 선택 시트를 띄운다.
	 */
	private List<MobileScheduleListResponse.MobileStreamLink> streamLinks(LeagueMatch match) {
		if (!"inProgress".equalsIgnoreCase(match.getState())) {
			return List.of();
		}
		return LeagueConstants.getStreamLinks(match.getLeagueName()).stream()
				.map(link -> new MobileScheduleListResponse.MobileStreamLink(
						link.provider(), link.label(), link.description(), link.url()))
				.toList();
	}

	private String toScheduledTime(LeagueMatch match) {
		if (match.getMatchDate() == null) {
			return "";
		}
		return match.getMatchDate()
				.atZone(UTC)
				.withZoneSameInstant(KST)
				.toLocalTime()
				.format(TIME_FORMATTER);
	}

	private LocalDate toKstDate(LeagueMatch match) {
		return match.getMatchDate()
				.atZone(UTC)
				.withZoneSameInstant(KST)
				.toLocalDate();
	}

	private LocalDateTime toUtc(LocalDateTime kstDateTime) {
		return kstDateTime.atZone(KST)
				.withZoneSameInstant(UTC)
				.toLocalDateTime();
	}

	private TeamFilter resolveTeamFilter(Long teamId) {
		if (teamId == null) {
			return null;
		}
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new CustomException(ErrorCode.DATA_NOT_FOUND));
		return new TeamFilter(team.getName(), team.getCode());
	}

	/** 복수 팀 필터. teamId 미전송이면 null(팀 조건 없음). 이름·코드는 소문자로 정규화해 IN 비교에 쓴다. */
	private TeamFilters resolveTeamFilters(List<Long> teamIds) {
		if (teamIds == null || teamIds.isEmpty()) {
			return null;
		}
		List<String> names = new ArrayList<>();
		List<String> codes = new ArrayList<>();
		for (Long teamId : teamIds) {
			Team team = teamRepository.findById(teamId)
					.orElseThrow(() -> new CustomException(ErrorCode.DATA_NOT_FOUND));
			names.add(team.getName().toLowerCase(Locale.ROOT));
			if (team.getCode() != null && !team.getCode().isBlank()) {
				codes.add(team.getCode().toLowerCase(Locale.ROOT));
			}
		}
		return new TeamFilters(names, codes.isEmpty() ? null : codes);
	}

	/** 리스트 리그 정규화. 비었으면 기본 리그. 각 원소는 단일 정규화(검증·대문자·ALL 처리)를 재사용한다. */
	private List<String> normalizeLeagues(List<String> leagues) {
		if (leagues == null || leagues.isEmpty()) {
			return List.of(DEFAULT_LEAGUE);
		}
		return leagues.stream().map(this::normalizeLeague).distinct().toList();
	}

	/** 리포지토리 필터용 리그 목록. ALL 이 하나라도 있으면 null 을 반환해 리그 조건을 걷는다. */
	private List<String> leagueParams(List<String> normalizedLeagues) {
		return normalizedLeagues.contains(ALL_LEAGUES) ? null : normalizedLeagues;
	}

	/** 응답 에코용 리그 값(하위호환: 단일 String 필드). 첫 리그만 반영한다. */
	private String echoLeague(List<String> normalizedLeagues) {
		return normalizedLeagues.get(0);
	}

	/** 응답 에코용 팀 값(하위호환: 단일 Long 필드). 첫 팀만 반영한다. */
	private Long echoTeamId(List<Long> teamIds) {
		return teamIds == null || teamIds.isEmpty() ? null : teamIds.get(0);
	}

	private String normalizeLeague(String league) {
		String normalized = league == null || league.isBlank()
				? DEFAULT_LEAGUE
				: league.trim().toUpperCase(Locale.ROOT);
		if (ALL_LEAGUES.equals(normalized)) {
			return ALL_LEAGUES;
		}
		if (!LeagueConstants.ALLOWED_LEAGUES.contains(normalized)) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return normalized;
	}

	/** 리포지토리 필터용 리그 값. 전체(ALL) 선택이면 null 을 반환해 리그 조건을 건다. */
	private String leagueParam(String normalizedLeague) {
		return ALL_LEAGUES.equals(normalizedLeague) ? null : normalizedLeague;
	}

	private record TeamFilter(String name, String code) {
	}

	private record TeamFilters(List<String> names, List<String> codes) {
	}

	private record MatchCursor(LocalDateTime matchDate, String matchId) {
	}
}
