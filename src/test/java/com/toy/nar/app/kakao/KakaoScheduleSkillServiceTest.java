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
	void todayUtteranceReturnsTextCardForDetectedLeague() {
		when(leagueMatchService.getMatchesFromDb(eq("LCK"), eq(LocalDate.now(ZoneId.of("Asia/Seoul")).toString())))
				.thenReturn(MatchResponseWrapper.builder()
						.matches(List.of(unstartedMatch()))
						.build());

		KakaoSkillResponse response = service.handleSchedule(
				new KakaoSkillRequest(new KakaoSkillRequest.UserRequest("오늘 LCK 일정 알려줘")));

		assertThat(response.version()).isEqualTo("2.0");
		assertThat(response.template().outputs()).hasSize(1);
		assertThat(response.template().outputs().get(0).textCard()).isNotNull();
		assertThat(response.template().outputs().get(0).textCard().title()).contains("LCK 일정");
		assertThat(response.template().outputs().get(0).textCard().description())
				.contains("17:00 T1 vs GEN");
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
				.blueTeam(MatchResultDto.TeamInfo.builder().code("T1").name("T1").wins(0).build())
				.redTeam(MatchResultDto.TeamInfo.builder().code("GEN").name("Gen.G Esports").wins(0).build())
				.build();
	}
}
