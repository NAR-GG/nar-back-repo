package com.toy.nar.app.standings;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

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
	 * <p>v1 은 LCK 만 연다. LCK 는 네이버가 시즌 통산으로 세는데 Split 1 은 빼고 Split 2 부터
	 * 센다 — Split 1(1~3주차, 5경기 조별)은 포맷이 다르고, 지금 레전드/라이즈를 가른 근거가
	 * Split 2 정규이기 때문이다. 우리 DB 로 Split 2+3 정규만 합산해 네이버 10팀 × (승/패/득실)
	 * 30개 값이 전부 일치하는 것을 확인했다.
	 *
	 * <p>다른 리그는 네이버 leagueId 가 스플릿 단위(lec_2026_summer)라 스코프가 자동으로 맞는다.
	 * 열 때 splits 를 "현재 스플릿"으로 잡으면 된다.
	 */
	private record Scope(String naverTopLeagueId, List<String> splits, String scopeLabel) {
	}

	private static final Map<String, Scope> SCOPES = Map.of(
			"LCK", new Scope("lck", List.of("Split 2", "Split 3"), "정규시즌 통산"));

	private final NaverStandingsClient naverClient;
	private final LeagueMatchRepository leagueMatchRepository;

	@Cacheable(cacheNames = "leagueStandings", key = "#league")
	public StandingsResponse getStandings(String league) {
		String normalized = league == null ? "" : league.trim().toUpperCase();
		Scope scope = SCOPES.get(normalized);
		if (scope == null) {
			return unsupported(normalized, "BRACKET_ONLY");
		}

		List<NaverRankRow> ranking = naverClient.resolveLeagueId(scope.naverTopLeagueId())
				.map(naverClient::fetchRanking)
				.orElse(List.of());
		if (ranking.isEmpty()) {
			return unsupported(normalized, "UNAVAILABLE");
		}

		return assemble(normalized, scope, ranking, derive(normalized, scope));
	}

	/** 우리 DB 로 계산하는 부분. 조회 실패·시즌 미상이면 비어 있는 채로 넘어간다(순위는 그대로 나간다). */
	private record Derived(Map<String, TeamMetrics> metrics, LocalDateTime dataThrough) {

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
		LocalDateTime through = scoped.stream()
				.filter(m -> "completed".equalsIgnoreCase(m.getState()))
				.map(LeagueMatch::getMatchDate)
				.filter(Objects::nonNull)
				.max(LocalDateTime::compareTo)
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
			TeamMetrics m = metrics.get(r.teamCode());
			if (m != null) {
				remainingTotal += m.remaining();
				ourPlayed += m.wins() + m.losses();
			}
			grouped.computeIfAbsent(r.groupName(), k -> new ArrayList<>())
					.add(StandingsResponse.Row.builder()
							.rank(r.rank())
							.teamCode(r.teamCode())
							.teamName(r.teamName())
							.imageUrl(r.imageUrl())
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

	private StandingsResponse unsupported(String league, String reason) {
		return StandingsResponse.builder()
				.league(league)
				.supported(false)
				.reason(reason)
				.groups(List.of())
				.build();
	}
}
