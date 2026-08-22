package com.toy.nar.app.standings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.toy.nar.app.lolesports.repository.LeagueMatch;

/**
 * 경기 목록에서 팀별 파생 지표를 뽑는다 — 세트 승패·연속·잔여.
 *
 * <p>네이버 순위 API 는 집계 결과(순위·승패·세트 득실차)만 주고 경기 단위를 안 준다.
 * 그래서 세트 원값(21-9)·연속(3승) 같은 컬럼은 우리 DB 로 계산해야 한다.
 *
 * <p>순수 함수라 외부 의존이 없다. 입력은 이미 리그·스코프로 걸러진 경기 목록이다.
 */
public final class StandingsCalculator {

	private StandingsCalculator() {
	}

	/**
	 * 팀 하나의 파생 지표.
	 *
	 * @param wins       매치 승
	 * @param losses     매치 패
	 * @param setWins    세트 승 합
	 * @param setLosses  세트 패 합
	 * @param streak     연속 기록. 승이면 양수, 패면 음수, 경기가 없으면 0
	 * @param remaining  아직 안 끝난 정규 경기 수
	 */
	public record TeamMetrics(int wins, int losses, int setWins, int setLosses, int streak, int remaining) {

		public int setDiff() {
			return setWins - setLosses;
		}
	}

	/**
	 * 팀 코드 → 지표.
	 *
	 * @param matches 스코프로 걸러진 경기 (완료·미완료 모두). 시간순 정렬은 내부에서 한다.
	 */
	public static Map<String, TeamMetrics> compute(List<LeagueMatch> matches) {
		List<LeagueMatch> ordered = new ArrayList<>(matches);
		ordered.sort(Comparator.comparing(LeagueMatch::getMatchDate,
				Comparator.nullsLast(Comparator.naturalOrder())));

		Map<String, Accum> byTeam = new LinkedHashMap<>();
		for (LeagueMatch m : ordered) {
			String blue = m.getBlueTeamCode();
			String red = m.getRedTeamCode();
			// 브래킷의 빈 슬롯. 팀이 정해지기 전에는 어느 팀의 잔여 경기도 아니다.
			if (isPlaceholder(blue) || isPlaceholder(red)) {
				continue;
			}
			if (!isFinished(m)) {
				byTeam.computeIfAbsent(blue, k -> new Accum()).remaining++;
				byTeam.computeIfAbsent(red, k -> new Accum()).remaining++;
				continue;
			}
			int blueScore = orZero(m.getBlueScore());
			int redScore = orZero(m.getRedScore());
			apply(byTeam.computeIfAbsent(blue, k -> new Accum()), blueScore, redScore);
			apply(byTeam.computeIfAbsent(red, k -> new Accum()), redScore, blueScore);
		}

		Map<String, TeamMetrics> result = new LinkedHashMap<>();
		byTeam.forEach((team, a) -> result.put(team,
				new TeamMetrics(a.wins, a.losses, a.setWins, a.setLosses, a.streak, a.remaining)));
		return result;
	}

	/**
	 * 완료 판정.
	 *
	 * <p>{@code state} 만 믿지 않는다. 라이브 경로가 스코어를 먼저 넣고 {@code state} 는
	 * 나중에 옮기는 구간이 있어서(되돌림 재발 방지로 applyScore 가 state 를 안 건드린다),
	 * 그 사이 경기가 순위에서 통째로 빠진다. 스코어로 승부가 갈렸으면 끝난 것으로 본다.
	 */
	private static boolean isFinished(LeagueMatch m) {
		if ("completed".equalsIgnoreCase(m.getState())) {
			return true;
		}
		int blue = orZero(m.getBlueScore());
		int red = orZero(m.getRedScore());
		if (blue == red) {
			return false;
		}
		// bestOf 가 없으면 Bo3 로 가정한다(리그 정규는 대부분 Bo3).
		int needed = (orDefault(m.getBestOf(), 3) / 2) + 1;
		return Math.max(blue, red) >= needed;
	}

	private static void apply(Accum a, int own, int opp) {
		a.setWins += own;
		a.setLosses += opp;
		boolean win = own > opp;
		if (win) {
			a.wins++;
			a.streak = a.streak > 0 ? a.streak + 1 : 1;
		} else {
			a.losses++;
			a.streak = a.streak < 0 ? a.streak - 1 : -1;
		}
	}

	private static boolean isPlaceholder(String teamCode) {
		return teamCode == null || teamCode.isBlank() || "TBD".equalsIgnoreCase(teamCode);
	}

	private static int orZero(Integer v) {
		return v == null ? 0 : v;
	}

	private static int orDefault(Integer v, int fallback) {
		return v == null || v <= 0 ? fallback : v;
	}

	private static final class Accum {
		int wins;
		int losses;
		int setWins;
		int setLosses;
		int streak;
		int remaining;
	}
}
