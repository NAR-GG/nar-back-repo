package com.toy.nar.app.standings;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.toy.nar.app.lolesports.LeagueConstants;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.standings.NaverStandingsClient.NaverRankRow;
import com.toy.nar.app.standings.StandingsCalculator.TeamMetrics;
import com.toy.nar.app.standings.dto.StandingsResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 리그 순위표를 조립한다.
 *
 * <p>두 소스를 합친다. 순위·승패·세트 득실차는 네이버(유저가 보는 화면과 같아야 하므로),
 * 세트 원값·연속·잔여는 우리 DB(네이버가 경기 단위를 안 주므로).
 *
 * <p>캐시는 5분 TTL 이고 이벤트 무효화를 넣지 않았다. 경기가 끝나도 상류(네이버)가 먼저 갱신돼야
 * 하는데, 우리가 완료를 먼저 감지해 evict 하면 아직 안 바뀐 값을 다시 캐시해 오히려 더 오래
 * 낡는다. 짧은 TTL 폴링이 더 단순하고 안전하다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StandingsService {

	/**
	 * 리그별 집계 스코프.
	 *
	 * <p>LCK 는 네이버가 시즌 통산으로 세는데 Split 1 은 빼고 Split 2 부터
	 * 센다 — Split 1(1~3주차, 5경기 조별)은 포맷이 다르고, 지금 레전드/라이즈를 가른 근거가
	 * Split 2 정규이기 때문이다. 우리 DB 로 Split 2+3 정규만 합산해 네이버 10팀 × (승/패/득실)
	 * 30개 값이 전부 일치하는 것을 확인했다.
	 *
	 * <p>다른 리그는 네이버 leagueId 가 스플릿 단위(lec_2026_summer)라 스코프가 자동으로 맞는다.
	 * 열 때 splits 를 "현재 스플릿"으로 잡으면 된다.
	 *
	 * <p>{@code fallbackLeagueId} 는 네이버 시즌 목록(meta/leagues)에서 시즌이 빠졌을 때 쓰는 마지막
	 * 방어선이다. 목록에서 사라져도 ranking 엔드포인트는 살아 있는 경우가 있었다(LCK 2026-09).
	 *
	 * <p>아시안게임·데마시아 컵은 {@code splits} 가 비어 있다 — 우리 DB 파생 지표(세트 득실·연속·잔여)를
	 * 계산하지 않고 네이버 순위만 내려준다. 둘 다 정규 주차가 없는 단기 대회라 파생할 대상이 없다.
	 */
	private record Scope(String naverTopLeagueId, List<String> splits, String scopeLabel, String fallbackLeagueId) {
	}

	/**
	 * 네이버 팀 코드 → 우리 코드. lolesports 와 코드가 다른 팀만 적는다(아시안게임 베트남: VNM → VIE).
	 * 앱이 일정의 팀 코드로 로고를 찾으므로 순위표도 같은 코드여야 한다.
	 */
	private static final Map<String, String> NAVER_CODE_ALIAS = Map.of("VNM", "VIE");

	private static final Map<String, Scope> SCOPES = Map.of(
			"LCK", new Scope("lck", List.of("Split 2", "Split 3"), "정규시즌 통산", "lck_2026"),
			"ASIAN_GAMES", new Scope("ag_lol", List.of(), "그룹 스테이지", "ag_lol_2026"),
			"DEMACIA_CUP", new Scope("dcgi", List.of(), "스위스 스테이지", "dcgi_2026"));

	/**
	 * 네이버 순위가 비면 우리 DB 경기 결과로 순위를 만드는 리그. 스위스 스테이지라 네이버가 집계를 안 준다.
	 * 네이버가 채우기 시작하면 위 분기가 먼저 타서 네이버 값이 우선한다.
	 */
	private static final java.util.Set<String> DB_STANDINGS_LEAGUES = java.util.Set.of("DEMACIA_CUP");

	/** 순위표를 등록한 리그인가. 모바일 필터의 순위표 칩 활성 여부가 이걸 따른다. */
	public static boolean hasScope(String league) {
		return SCOPES.containsKey(league);
	}

	private final NaverStandingsClient naverClient;
	private final LeagueMatchRepository leagueMatchRepository;
	private final LolesportsStageClient stageClient;

	@Cacheable(cacheNames = "leagueStandings", key = "#league")
	public StandingsResponse getStandings(String league) {
		String normalized = league == null ? "" : league.trim().toUpperCase();
		Scope scope = SCOPES.get(normalized);
		if (scope == null) {
			return unsupported(normalized, "BRACKET_ONLY");
		}

		String leagueId = naverClient.resolveLeagueId(scope.naverTopLeagueId()).orElse(scope.fallbackLeagueId());
		List<NaverRankRow> ranking = naverClient.fetchRanking(leagueId);
		if (ranking.isEmpty()) {
			// 네이버가 스위스 순위를 비워 둔다(데마시아 컵 2026-10 실측: content=[]). 우리 DB 로 대신 센다.
			if (DB_STANDINGS_LEAGUES.contains(normalized)) {
				return fromDb(normalized, scope).orElseGet(() -> unsupported(normalized, "UNAVAILABLE"));
			}
			return unsupported(normalized, "UNAVAILABLE");
		}

		Derived derived = scope.splits().isEmpty() ? Derived.empty() : derive(normalized, scope);
		return assemble(normalized, scope, ranking, derived);
	}

	/** 우리 DB 로 계산하는 부분. 조회 실패·시즌 미상이면 비어 있는 채로 넘어간다(순위는 그대로 나간다). */
	private record Derived(Map<String, TeamMetrics> metrics, OffsetDateTime dataThrough) {

		static Derived empty() {
			return new Derived(Map.of(), null);
		}
	}

	private Derived derive(String league, Scope scope) {
		LeagueMatch latest = leagueMatchRepository.findTopByLeagueNameOrderByMatchDateDesc(league);
		if (latest == null || latest.getSeasonYear() == null) {
			log.info("순위 파생 지표를 건너뛴다 — 시즌 정보 없음: league={}", league);
			return Derived.empty();
		}
		List<LeagueMatch> scoped = leagueMatchRepository
				.findForStandings(league, latest.getSeasonYear(), scope.splits())
				.stream()
				.filter(m -> StandingsBlocks.isRegular(m.getMatchTitle()))
				.toList();
		if (scoped.isEmpty()) {
			return Derived.empty();
		}
		// match_date 는 오프셋 없는 UTC 벽시계다. 여기서 오프셋을 붙여 내보내야
		// 앱이 로컬 시각으로 오해하지 않는다.
		OffsetDateTime through = scoped.stream()
				.filter(m -> "completed".equalsIgnoreCase(m.getState()))
				.map(LeagueMatch::getMatchDate)
				.filter(Objects::nonNull)
				.max(LocalDateTime::compareTo)
				.map(d -> d.atOffset(ZoneOffset.UTC))
				.orElse(null);
		return new Derived(StandingsCalculator.compute(scoped), through);
	}

	private StandingsResponse assemble(String league, Scope scope,
			List<NaverRankRow> ranking, Derived derived) {

		Map<String, TeamMetrics> metrics = derived.metrics();
		Map<String, List<StandingsResponse.Row>> grouped = new LinkedHashMap<>();
		int remainingTotal = 0;
		int ourPlayed = 0;
		for (NaverRankRow r : ranking) {
			String code = NAVER_CODE_ALIAS.getOrDefault(r.teamCode(), r.teamCode());
			TeamMetrics m = metrics.get(code);
			if (m != null) {
				remainingTotal += m.remaining();
				ourPlayed += m.wins() + m.losses();
			}
			grouped.computeIfAbsent(r.groupName(), k -> new ArrayList<>())
					.add(StandingsResponse.Row.builder()
							.rank(r.rank())
							.teamCode(code)
							.teamName(r.teamName())
							.imageUrl(Optional.ofNullable(LeagueConstants.nationalTeamImage(code)).orElse(r.imageUrl()))
							.wins(r.wins())
							.losses(r.losses())
							.setDiff(r.setDiff())
							.setWins(m == null ? null : m.setWins())
							.setLosses(m == null ? null : m.setLosses())
							.streak(m == null ? null : m.streak())
							.remaining(m == null ? null : m.remaining())
							.build());
		}

		// 두 소스가 같은 경기 집합을 보고 있는지. 팀별 (승+패) 합은 경기 수의 2배다.
		int naverPlayed = ranking.stream().mapToInt(r -> r.wins() + r.losses()).sum();
		boolean inSync = metrics.isEmpty() || naverPlayed == ourPlayed;
		if (!inSync) {
			log.info("순위 소스 불일치 — league={} naver={}경기 db={}경기", league, naverPlayed / 2, ourPlayed / 2);
		}

		List<StandingsResponse.Group> groups = grouped.entrySet().stream()
				.map(e -> StandingsResponse.Group.builder().name(e.getKey()).rows(e.getValue()).build())
				.toList();

		return StandingsResponse.builder()
				.league(league)
				.supported(true)
				.scopeLabel(scope.scopeLabel())
				.regularFinished(!metrics.isEmpty() && remainingTotal == 0)
				.dataThrough(derived.dataThrough())
				.inSync(inSync)
				.groups(groups)
				.build();
	}

	/**
	 * 우리 DB 결과로 만든 순위. 완료된 정규(스위스) 경기가 하나도 없으면 비어 있다 — 시작 전과 구분된다.
	 *
	 * <p>정렬은 승 → 패 → 세트 득실차 순이고, 셋이 같으면 공동 순위다. 스위스 공식 타이브레이크
	 * (Buchholz 등)는 반영하지 않는다 — 동률 팀의 순서가 대회 공식 순위와 다를 수 있다.
	 */
	private Optional<StandingsResponse> fromDb(String league, Scope scope) {
		LeagueMatch latest = leagueMatchRepository.findTopByLeagueNameOrderByMatchDateDesc(league);
		if (latest == null || latest.getSeasonYear() == null || latest.getSeasonSplit() == null) {
			return Optional.empty();
		}
		List<LeagueMatch> scoped = leagueMatchRepository
				.findForStandings(league, latest.getSeasonYear(), List.of(latest.getSeasonSplit()))
				.stream()
				.filter(m -> StandingsBlocks.isRegular(m.getMatchTitle()))
				.toList();
		// 제목 블록명이 같은 Round 4(0-2 팀 풀리그)가 섞이지 않게 lolesports 의 Swiss 스테이지만 센다.
		// 조회가 실패하면 전체로 계산한다 — 순위가 아예 없는 것보다 낫다.
		Optional<java.util.Set<String>> swissIds = stageClient.stageMatchIds(LeagueConstants.LEAGUE_IDS.get(league), "Swiss");
		if (swissIds.isPresent()) {
			scoped = scoped.stream().filter(m -> swissIds.get().contains(m.getId())).toList();
		}
		Map<String, TeamMetrics> metrics = StandingsCalculator.compute(scoped);
		if (metrics.values().stream().noneMatch(m -> m.wins() + m.losses() > 0)) {
			return Optional.empty();
		}

		Map<String, String[]> nameAndImage = new LinkedHashMap<>();
		for (LeagueMatch m : scoped) {
			nameAndImage.putIfAbsent(m.getBlueTeamCode(), new String[] { m.getBlueTeamName(), m.getBlueTeamImageUrl() });
			nameAndImage.putIfAbsent(m.getRedTeamCode(), new String[] { m.getRedTeamName(), m.getRedTeamImageUrl() });
		}

		List<Map.Entry<String, TeamMetrics>> sorted = new ArrayList<>(metrics.entrySet());
		sorted.sort(java.util.Comparator
				.comparingInt((Map.Entry<String, TeamMetrics> e) -> -e.getValue().wins())
				.thenComparingInt(e -> e.getValue().losses())
				.thenComparingInt(e -> -e.getValue().setDiff()));

		List<StandingsResponse.Row> rows = new ArrayList<>();
		TeamMetrics prev = null;
		int rank = 0;
		for (int i = 0; i < sorted.size(); i++) {
			TeamMetrics m = sorted.get(i).getValue();
			boolean tied = prev != null && prev.wins() == m.wins() && prev.losses() == m.losses()
					&& prev.setDiff() == m.setDiff();
			if (!tied) {
				rank = i + 1;
			}
			prev = m;
			String code = sorted.get(i).getKey();
			String[] info = nameAndImage.getOrDefault(code, new String[] { code, null });
			rows.add(StandingsResponse.Row.builder()
					.rank(rank)
					.teamCode(code)
					.teamName(info[0] != null ? info[0] : code)
					.imageUrl(info[1])
					.wins(m.wins())
					.losses(m.losses())
					.setDiff(m.setDiff())
					.setWins(m.setWins())
					.setLosses(m.setLosses())
					.streak(m.streak())
					.remaining(m.remaining())
					.build());
		}

		OffsetDateTime through = scoped.stream()
				.filter(m -> "completed".equalsIgnoreCase(m.getState()))
				.map(LeagueMatch::getMatchDate)
				.filter(Objects::nonNull)
				.max(LocalDateTime::compareTo)
				.map(d -> d.atOffset(ZoneOffset.UTC))
				.orElse(null);

		return Optional.of(StandingsResponse.builder()
				.league(league)
				.supported(true)
				.scopeLabel(scope.scopeLabel())
				.regularFinished(metrics.values().stream().allMatch(m -> m.remaining() == 0))
				.dataThrough(through)
				.inSync(true)
				.groups(List.of(StandingsResponse.Group.builder().name(scope.scopeLabel()).rows(rows).build()))
				.build());
	}

	private StandingsResponse unsupported(String league, String reason) {
		return StandingsResponse.builder()
				.league(league)
				.supported(false)
				.reason(reason)
				.groups(List.of())
				.build();
	}
}
