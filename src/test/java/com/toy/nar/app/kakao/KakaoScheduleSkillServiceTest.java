package com.toy.nar.app.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.toy.nar.api.kakao.dto.KakaoSkillRequest;
import com.toy.nar.api.kakao.dto.KakaoSkillResponse;
import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.MatchResponseWrapper;
import com.toy.nar.app.lolesports.MatchResultDto;

class KakaoScheduleSkillServiceTest {

	private final LeagueMatchService leagueMatchService = Mockito.mock(LeagueMatchService.class);
	private final KakaoScheduleSkillService service = new KakaoScheduleSkillService(leagueMatchService);

	@Test
	void todayUtteranceReturnsListCardForDetectedLeague() {
		when(leagueMatchService.getMatchesFromDb(eq("LCK"), eq(LocalDate.now(ZoneId.of("Asia/Seoul")).toString())))
				.thenReturn(MatchResponseWrapper.builder()
						.matches(List.of(unstartedMatch()))
						.build());

		KakaoSkillResponse response = service.handleSchedule(
				new KakaoSkillRequest(new KakaoSkillRequest.UserRequest("오늘 LCK 일정 알려줘")));

		assertThat(response.version()).isEqualTo("2.0");
		assertThat(response.template().outputs()).hasSize(1);
		assertThat(response.template().outputs().get(0).listCard()).isNotNull();
		assertThat(response.template().outputs().get(0).listCard().header().title()).contains("LCK 일정");
		assertThat(response.template().outputs().get(0).listCard().items().get(0).title()).isEqualTo("T1 vs GEN");
		assertThat(response.template().outputs().get(0).listCard().items().get(0).imageUrl())
				.isEqualTo("https://img.example.com/t1.png");
	}

	@Test
	void thisWeekUtteranceUsesWeekRange() {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
		LocalDate weekEnd = weekStart.plusDays(6);

		when(leagueMatchService.getMatchesFromDb(eq("LCK"), eq(weekStart), eq(weekEnd)))
				.thenReturn(MatchResponseWrapper.builder()
						.matches(List.of(unstartedMatch()))
						.build());

		KakaoSkillResponse response = service.handleSchedule(
				new KakaoSkillRequest(new KakaoSkillRequest.UserRequest("이번주 LCK 일정")));

		assertThat(response.template().outputs().get(0).listCard().header().title()).contains("이번주");
		assertThat(response.template().outputs().get(0).listCard().items().get(0).description()).contains("3월 1일 17:00");
	}

	@Test
	void noMatchesReturnsTextCard() {
		when(leagueMatchService.getMatchesFromDb(eq("LCK"), eq(LocalDate.now(ZoneId.of("Asia/Seoul")).toString())))
				.thenReturn(MatchResponseWrapper.builder()
						.matches(List.of())
						.build());

		KakaoSkillResponse response = service.handleSchedule(
				new KakaoSkillRequest(new KakaoSkillRequest.UserRequest("오늘 LCK 일정")));

		assertThat(response.template().outputs().get(0).textCard()).isNotNull();
		assertThat(response.template().outputs().get(0).listCard()).isNull();
	}

	@Test
	void lplIsDetectedFromUtterance() {
		String league = service.resolveLeague("내일 LPL 일정");

		assertThat(league).isEqualTo("LPL");
	}

	@Test
	void fstIsDetectedFromUtterance() {
		String league = service.resolveLeague("오늘 FST 일정");

		assertThat(league).isEqualTo("FIRST_STAND");
	}

	@Test
	void weekendRangeIsResolvedFromUtterance() {
		KakaoScheduleSkillService.QueryPeriod period = service.resolveQueryPeriod("주말 LCK 일정");

		assertThat(period.startDate().getDayOfWeek().getValue()).isEqualTo(6);
		assertThat(period.endDate()).isEqualTo(period.startDate().plusDays(1));
		assertThat(period.label()).contains("주말");
	}

	@Test
	void nextWeekRangeIsResolvedFromUtterance() {
		KakaoScheduleSkillService.QueryPeriod period = service.resolveQueryPeriod("다음주 LCK 일정");

		assertThat(period.startDate().getDayOfWeek().getValue()).isEqualTo(1);
		assertThat(period.endDate()).isEqualTo(period.startDate().plusDays(6));
		assertThat(period.label()).contains("다음주");
	}

	@Test
	void explicitIsoDateIsParsedFromUtterance() {
		LocalDate parsed = service.resolveTargetDate("2026-03-01 LCK 일정");

		assertThat(parsed).isEqualTo(LocalDate.of(2026, 3, 1));
	}

	private MatchResultDto unstartedMatch() {
		return MatchResultDto.builder()
				.matchId("match-1")
				.leagueName("LCK")
				.matchDate("2026-03-01T08:00")
				.state("unstarted")
				.score("0 : 0")
				.blueTeam(MatchResultDto.TeamInfo.builder().code("T1").name("T1").imageUrl("https://img.example.com/t1.png").wins(0).build())
				.redTeam(MatchResultDto.TeamInfo.builder().code("GEN").name("Gen.G Esports").imageUrl("https://img.example.com/gen.png").wins(0).build())
				.build();
	}
}
