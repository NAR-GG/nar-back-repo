package com.toy.nar.app.standings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.app.standings.NaverStandingsClient.NaverRankRow;
import com.toy.nar.app.standings.dto.StandingsResponse;

class StandingsServiceTest {

	private NaverStandingsClient naver;
	private LeagueMatchRepository repository;
	private StandingsService service;

	@BeforeEach
	void setUp() {
		naver = mock(NaverStandingsClient.class);
		repository = mock(LeagueMatchRepository.class);
		service = new StandingsService(naver, repository);
		when(naver.resolveLeagueId("lck")).thenReturn(Optional.of("lck_2026"));
	}

	private static NaverRankRow rank(int rank, String code, String group, int w, int l, int diff) {
		return new NaverRankRow(code, code, null, group, rank, w, l, diff);
	}

	private static LeagueMatch match(String date, String blue, int bs, int rs, String red, String state) {
		return LeagueMatch.builder()
				.id(date + blue).leagueName("LCK").matchTitle("13주 차 | " + blue + " vs " + red)
				.matchDate(LocalDateTime.parse(date)).state(state)
				.blueTeamCode(blue).blueScore(bs).redTeamCode(red).redScore(rs)
				.seasonYear(2026).seasonSplit("Split 3").bestOf(3).build();
	}

	private void givenMatches(List<LeagueMatch> matches) {
		LeagueMatch latest = matches.isEmpty()
				? match("2026-08-19T08:00", "GEN", 2, 1, "KT", "completed")
				: matches.get(matches.size() - 1);
		when(repository.findTopByLeagueNameOrderByMatchDateDesc("LCK")).thenReturn(latest);
		when(repository.findForStandings(anyString(), anyInt(), any())).thenReturn(matches);
	}

	@DisplayName("모르는 리그는 supported=false 로 내려간다")
	@Test
	void unknownLeagueIsUnsupported() {
		StandingsResponse res = service.getStandings("WORLDS");

		assertThat(res.supported()).isFalse();
		assertThat(res.reason()).isEqualTo("BRACKET_ONLY");
		assertThat(res.groups()).isEmpty();
	}

	@DisplayName("네이버 조회가 비면 UNAVAILABLE 이다 — 순위가 없는 것과 구분한다")
	@Test
	void emptyRankingIsUnavailable() {
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of());

		StandingsResponse res = service.getStandings("LCK");

		assertThat(res.supported()).isFalse();
		assertThat(res.reason()).isEqualTo("UNAVAILABLE");
	}

	@DisplayName("네이버 시즌 목록에서 시즌이 빠져도 폴백 id 로 순위를 준다 — 2026-09 LCK 실사고")
	@Test
	void usesFallbackLeagueIdWhenSeasonMissingFromList() {
		when(naver.resolveLeagueId("lck")).thenReturn(Optional.empty());
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of(rank(1, "GEN", "LEGEND", 19, 7, 22)));
		givenMatches(List.of());

		StandingsResponse res = service.getStandings("LCK");

		assertThat(res.supported()).isTrue();
		assertThat(res.groups().get(0).rows().get(0).teamCode()).isEqualTo("GEN");
	}

	@DisplayName("아시안게임은 네이버 그룹 순위를 주고 네이버 코드를 우리 코드로, 로고를 국기로 바꾼다")
	@Test
	void asianGamesGroupsUseOurCodesAndFlags() {
		when(naver.resolveLeagueId("ag_lol")).thenReturn(Optional.of("ag_lol_2026"));
		when(naver.fetchRanking("ag_lol_2026")).thenReturn(List.of(
				new NaverRankRow("VNM", "베트남", "https://naver/vnm.png", "A", 1, 3, 0, 3),
				new NaverRankRow("KSA", "사우디아라비아", "https://naver/ksa.png", "A", 2, 2, 1, 1),
				new NaverRankRow("KOR", "대한민국", "https://naver/kor.png", "B", 1, 3, 0, 3)));

		StandingsResponse res = service.getStandings("ASIAN_GAMES");

		assertThat(res.supported()).isTrue();
		assertThat(res.scopeLabel()).isEqualTo("그룹 스테이지");
		assertThat(res.groups()).extracting(StandingsResponse.Group::name).containsExactly("A", "B");
		StandingsResponse.Row first = res.groups().get(0).rows().get(0);
		assertThat(first.teamCode()).isEqualTo("VIE");
		assertThat(first.imageUrl()).isEqualTo("https://api.nar.kr/images/flags/vie.png");
		assertThat(first.teamName()).isEqualTo("베트남");
		assertThat(first.setWins()).isNull();
	}

	@DisplayName("데마시아 컵은 네이버가 비어 있으면(시작 전) UNAVAILABLE 이다")
	@Test
	void demaciaCupBeforeStartIsUnavailable() {
		when(naver.resolveLeagueId("dcgi")).thenReturn(Optional.of("dcgi_2026"));
		when(naver.fetchRanking("dcgi_2026")).thenReturn(List.of());

		StandingsResponse res = service.getStandings("DEMACIA_CUP");

		assertThat(res.supported()).isFalse();
		assertThat(res.reason()).isEqualTo("UNAVAILABLE");
	}

	private static LeagueMatch swiss(String date, String blue, int bs, int rs, String red, int bestOf) {
		return LeagueMatch.builder()
				.id(date + blue).leagueName("DEMACIA_CUP").matchTitle("스위스 | " + blue + " vs " + red)
				.matchDate(LocalDateTime.parse(date)).state("completed")
				.blueTeamCode(blue).blueTeamName(blue + " name").blueScore(bs)
				.redTeamCode(red).redTeamName(red + " name").redScore(rs)
				.seasonYear(2026).seasonSplit("Cup").bestOf(bestOf).build();
	}

	@DisplayName("데마시아 컵은 네이버가 비어도 우리 DB 결과로 스위스 순위를 만든다 — 승 → 패 → 세트 득실, 같으면 공동")
	@Test
	void demaciaCupFallsBackToDbWhenNaverEmpty() {
		when(naver.resolveLeagueId("dcgi")).thenReturn(Optional.of("dcgi_2026"));
		when(naver.fetchRanking("dcgi_2026")).thenReturn(List.of());
		List<LeagueMatch> matches = List.of(
				swiss("2026-10-03T08:00", "RED", 1, 0, "NAVI", 1),
				swiss("2026-10-03T09:00", "FLY", 0, 1, "LGD", 1),
				swiss("2026-10-04T07:00", "BFX", 2, 1, "LGD", 3),
				swiss("2026-10-04T09:00", "KT", 2, 0, "RED", 3),
				swiss("2026-10-05T09:00", "FLY", 0, 2, "NAVI", 3));
		when(repository.findTopByLeagueNameOrderByMatchDateDesc("DEMACIA_CUP")).thenReturn(matches.get(4));
		when(repository.findForStandings("DEMACIA_CUP", 2026, List.of("Cup"))).thenReturn(matches);

		StandingsResponse res = service.getStandings("DEMACIA_CUP");

		assertThat(res.supported()).isTrue();
		assertThat(res.scopeLabel()).isEqualTo("스위스 스테이지");
		assertThat(res.groups()).hasSize(1);
		List<StandingsResponse.Row> rows = res.groups().get(0).rows();
		// 1-0: KT(+2) BFX(+1) / 1-1: NAVI(+1) LGD(0) RED(-1) / 0-2: FLY. 승 → 패 → 세트 득실 순.
		assertThat(rows).extracting(StandingsResponse.Row::teamCode)
				.containsExactly("KT", "BFX", "NAVI", "LGD", "RED", "FLY");
		assertThat(rows).extracting(StandingsResponse.Row::rank).containsExactly(1, 2, 3, 4, 5, 6);
		assertThat(rows.get(0).wins()).isEqualTo(1);
		assertThat(rows.get(0).teamName()).isEqualTo("KT name");
		assertThat(res.inSync()).isTrue();
	}

	@DisplayName("스위스 DB 순위는 승·패·세트 득실이 모두 같으면 공동 순위다")
	@Test
	void demaciaCupDbRankSharesTies() {
		when(naver.resolveLeagueId("dcgi")).thenReturn(Optional.of("dcgi_2026"));
		when(naver.fetchRanking("dcgi_2026")).thenReturn(List.of());
		List<LeagueMatch> matches = List.of(
				swiss("2026-10-03T08:00", "RED", 1, 0, "NAVI", 1),
				swiss("2026-10-03T09:00", "KT", 1, 0, "SR", 1));
		when(repository.findTopByLeagueNameOrderByMatchDateDesc("DEMACIA_CUP")).thenReturn(matches.get(1));
		when(repository.findForStandings("DEMACIA_CUP", 2026, List.of("Cup"))).thenReturn(matches);

		List<StandingsResponse.Row> rows = service.getStandings("DEMACIA_CUP").groups().get(0).rows();

		assertThat(rows).extracting(StandingsResponse.Row::rank).containsExactly(1, 1, 3, 3);
	}

	@DisplayName("그룹별로 나누고 네이버 rank 를 그대로 쓴다")
	@Test
	void groupsRowsAndKeepsNaverRank() {
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of(
				rank(1, "GEN", "LEGEND", 19, 6, 23),
				rank(2, "HLE", "LEGEND", 18, 7, 20),
				rank(1, "BFX", "RISE", 10, 15, -10)));
		givenMatches(List.of());

		StandingsResponse res = service.getStandings("LCK");

		assertThat(res.supported()).isTrue();
		assertThat(res.groups()).hasSize(2);
		assertThat(res.groups().get(0).name()).isEqualTo("LEGEND");
		assertThat(res.groups().get(0).rows()).extracting(StandingsResponse.Row::teamCode)
				.containsExactly("GEN", "HLE");
		assertThat(res.groups().get(1).rows().get(0).rank()).isEqualTo(1); // 그룹 안에서 1위
		assertThat(res.groups().get(0).rows().get(0).setDiff()).isEqualTo(23);
	}

	@DisplayName("우리 DB 집계가 붙으면 세트 원값·연속·잔여가 채워진다")
	@Test
	void mergesDerivedMetrics() {
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of(
				rank(1, "GEN", null, 1, 0, 1),
				rank(2, "KT", null, 0, 1, -1)));
		givenMatches(List.of(match("2026-08-19T08:00", "GEN", 2, 1, "KT", "completed")));

		StandingsResponse.Row gen = res(service.getStandings("LCK"), "GEN");

		assertThat(gen.setWins()).isEqualTo(2);
		assertThat(gen.setLosses()).isEqualTo(1);
		assertThat(gen.streak()).isEqualTo(1);
		assertThat(gen.remaining()).isZero();
	}

	@DisplayName("네이버와 우리 DB 의 경기 수가 다르면 inSync=false")
	@Test
	void detectsSourceLag() {
		// 네이버는 2경기를 반영했는데 우리 DB 엔 1경기만 들어와 있다.
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of(
				rank(1, "GEN", null, 2, 0, 3),
				rank(2, "KT", null, 0, 2, -3)));
		givenMatches(List.of(match("2026-08-19T08:00", "GEN", 2, 1, "KT", "completed")));

		assertThat(service.getStandings("LCK").inSync()).isFalse();
	}

	@DisplayName("경기 수가 맞으면 inSync=true")
	@Test
	void inSyncWhenCountsMatch() {
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of(
				rank(1, "GEN", null, 1, 0, 1),
				rank(2, "KT", null, 0, 1, -1)));
		givenMatches(List.of(match("2026-08-19T08:00", "GEN", 2, 1, "KT", "completed")));

		assertThat(service.getStandings("LCK").inSync()).isTrue();
	}

	@DisplayName("잔여 경기가 0 이면 정규 종료로 본다")
	@Test
	void regularFinishedWhenNothingLeft() {
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of(
				rank(1, "GEN", null, 1, 0, 1),
				rank(2, "KT", null, 0, 1, -1)));
		givenMatches(List.of(match("2026-08-19T08:00", "GEN", 2, 1, "KT", "completed")));

		StandingsResponse res = service.getStandings("LCK");

		assertThat(res.regularFinished()).isTrue();
		assertThat(res.dataThrough())
				.isEqualTo(LocalDateTime.parse("2026-08-19T08:00").atOffset(ZoneOffset.UTC));
	}

	@DisplayName("남은 경기가 있으면 정규 진행 중이다")
	@Test
	void notFinishedWhileMatchesRemain() {
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of(
				rank(1, "GEN", null, 1, 0, 1),
				rank(2, "KT", null, 0, 1, -1)));
		givenMatches(List.of(
				match("2026-08-19T08:00", "GEN", 2, 1, "KT", "completed"),
				match("2026-08-22T08:00", "GEN", 0, 0, "KT", "unstarted")));

		assertThat(service.getStandings("LCK").regularFinished()).isFalse();
	}

	@DisplayName("DB 집계가 없어도 순위는 그대로 내려간다 — 파생 컬럼만 null")
	@Test
	void survivesWithoutDerivedMetrics() {
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of(rank(1, "GEN", null, 19, 6, 23)));
		when(repository.findTopByLeagueNameOrderByMatchDateDesc("LCK")).thenReturn(null);

		StandingsResponse res = service.getStandings("LCK");
		StandingsResponse.Row gen = res(res, "GEN");

		assertThat(res.supported()).isTrue();
		assertThat(gen.wins()).isEqualTo(19);
		assertThat(gen.setWins()).isNull();
		assertThat(gen.streak()).isNull();
		assertThat(res.inSync()).isTrue(); // 비교할 대상이 없으면 불일치로 보지 않는다
	}

	@DisplayName("dataThrough 는 UTC 오프셋을 달고 나간다 — match_date 가 오프셋 없는 UTC 라서")
	@Test
	void dataThroughCarriesUtcOffset() {
		when(naver.fetchRanking("lck_2026")).thenReturn(List.of(
				rank(1, "GEN", null, 1, 0, 1),
				rank(2, "KT", null, 0, 1, -1)));
		// 17:15Z = 다음 날 02:15 KST. 오프셋을 안 붙이면 앱이 하루 앞 날짜로 읽는다(LEC 가 이 시간대다).
		givenMatches(List.of(match("2026-08-21T17:15", "GEN", 2, 1, "KT", "completed")));

		OffsetDateTime through = service.getStandings("LCK").dataThrough();

		assertThat(through.getOffset()).isEqualTo(ZoneOffset.UTC);
		assertThat(through.toInstant())
				.isEqualTo(LocalDateTime.parse("2026-08-21T17:15").toInstant(ZoneOffset.UTC));
	}

	private static StandingsResponse.Row res(StandingsResponse response, String teamCode) {
		return response.groups().stream()
				.flatMap(g -> g.rows().stream())
				.filter(r -> r.teamCode().equals(teamCode))
				.findFirst()
				.orElseThrow();
	}
}
