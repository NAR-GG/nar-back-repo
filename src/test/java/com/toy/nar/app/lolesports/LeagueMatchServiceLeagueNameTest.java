package com.toy.nar.app.lolesports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.game.repository.GameExternalIdentityRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;

@ExtendWith(MockitoExtension.class)
class LeagueMatchServiceLeagueNameTest {

	@Mock
	private LeagueMatchRepository leagueMatchRepository;
	@Mock
	private LeagueMatchGameRepository leagueMatchGameRepository;
	@Mock
	private TeamRepository teamRepository;
	@Mock
	private TeamExternalIdentityRepository teamExternalIdentityRepository;
	@Mock
	private GameExternalIdentityRepository gameExternalIdentityRepository;
	@Mock
	private WorldsService worldsService;
	@Mock
	private TransactionTemplate transactionTemplate;
	@Mock
	private GameRepository gameRepository;

	private LeagueMatchService leagueMatchService;

	@BeforeEach
	void setUp() {
		leagueMatchService = new LeagueMatchService(
				leagueMatchRepository,
				leagueMatchGameRepository,
				teamRepository,
				teamExternalIdentityRepository,
				gameExternalIdentityRepository,
				worldsService,
				new ObjectMapper(),
				transactionTemplate,
				gameRepository);
	}

	@Test
	@DisplayName("상세 API 리그명이 있으면 요청 리그보다 상세 API 리그명을 DB 저장값으로 사용한다")
	void convertToEntity_prefersDetailLeagueName() {
		MatchResultDto dto = matchDto("LCK");

		LeagueMatch entity = ReflectionTestUtils.invokeMethod(
				leagueMatchService,
				"convertToEntity",
				dto,
				"FIRST_STAND");

		assertThat(entity).isNotNull();
		assertThat(entity.getLeagueName()).isEqualTo("LCK");
	}

	@Test
	@DisplayName("리그명만 바뀐 기존 경기 row도 업데이트 대상으로 감지한다")
	void hasRealtimeRelevantChange_detectsLeagueNameChange() {
		LeagueMatch existing = leagueMatch("FIRST_STAND");
		LeagueMatch incoming = leagueMatch("LCK");

		Boolean changed = ReflectionTestUtils.invokeMethod(
				leagueMatchService,
				"hasRealtimeRelevantChange",
				existing,
				incoming);

		assertThat(changed).isTrue();
	}

	private MatchResultDto matchDto(String leagueName) {
		return MatchResultDto.builder()
				.matchId("115548128962971911")
				.leagueName(leagueName)
				.matchTitle("8주 차 | T1 vs KRX")
				.matchDate("2026-05-20T08:00:00Z")
				.state("unstarted")
				.blueTeam(MatchResultDto.TeamInfo.builder()
						.externalTeamId("98767991853197861")
						.code("T1")
						.name("T1")
						.imageUrl("https://static.lolesports.com/teams/t1.png")
						.wins(0)
						.build())
				.redTeam(MatchResultDto.TeamInfo.builder()
						.externalTeamId("99566404585387054")
						.code("KRX")
						.name("KIWOOM DRX")
						.imageUrl("https://static.lolesports.com/teams/drx.png")
						.wins(0)
						.build())
				.sets(List.of())
				.build();
	}

	private LeagueMatch leagueMatch(String leagueName) {
		return LeagueMatch.builder()
				.id("115548128962971911")
				.leagueName(leagueName)
				.matchTitle("8주 차 | T1 vs KRX")
				.matchDate(LocalDateTime.of(2026, 5, 20, 8, 0))
				.state("unstarted")
				.blueTeamCode("T1")
				.blueTeamName("T1")
				.blueExternalTeamId("98767991853197861")
				.blueTeamImageUrl("https://static.lolesports.com/teams/t1.png")
				.blueScore(0)
				.redTeamCode("KRX")
				.redTeamName("KIWOOM DRX")
				.redExternalTeamId("99566404585387054")
				.redTeamImageUrl("https://static.lolesports.com/teams/drx.png")
				.redScore(0)
				.hasVod(false)
				.matchDetailsJson("[]")
				.lastUpdated(LocalDateTime.of(2026, 5, 19, 12, 0))
				.build();
	}
}
