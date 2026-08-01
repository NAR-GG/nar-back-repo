package com.toy.nar.app.lolesports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.nar.app.lolesports.repository.LeagueMatchGameRepository;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.game.repository.GameExternalIdentityRepository;
import com.toy.nar.domain.game.repository.GameRepository;
import com.toy.nar.domain.participant.repository.TeamExternalIdentityRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;

/**
 * getSchedule 미래(newer) 페이지 추적 검증.
 * 기본 페이지 창 밖의 미래 일정(LCK 플레이오프·결승, LPL 정규 잔여분)은 newer 토큰을 따라가야만 DB 에 들어온다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeagueMatchServiceNewerPageTest {

	@Mock
	private LeagueMatchRepository leagueMatchRepository;
	@Mock
	private LeagueMatchGameRepository leagueMatchGameRepository;
	@Mock
	private com.toy.nar.app.lolesports.season.LeagueSeasonResolver leagueSeasonResolver;
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
				leagueSeasonResolver,
				teamRepository,
				teamExternalIdentityRepository,
				gameExternalIdentityRepository,
				worldsService,
				new ObjectMapper(),
				transactionTemplate,
				gameRepository,
				null);
		when(leagueMatchRepository.findAllById(anyList())).thenReturn(List.of());
		when(leagueMatchGameRepository.findMappedGameRowsByMatchIds(anyList(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(List.of());
	}

	@Test
	@DisplayName("기본 페이지의 newer 토큰을 따라 미래 경기까지 저장한다")
	void syncMatches_followsNewerPage() {
		when(worldsService.getWorldsMatches(null, "LCK")).thenReturn(page(match("base-1"), "newer-1", null));
		when(worldsService.getWorldsMatches("newer-1", "LCK")).thenReturn(page(match("playoff-1"), null, null));

		leagueMatchService.syncMatchesWithoutTeamMetadata("LCK");

		verify(worldsService).getWorldsMatches("newer-1", "LCK");
		// 기본 페이지 1건 + 미래 페이지 1건이 각각 저장된다.
		verify(leagueMatchRepository, times(2)).saveAll(anyList());
	}

	@Test
	@DisplayName("newer 토큰이 없으면 추가 페이지를 조회하지 않는다")
	void syncMatches_stopsWithoutNewerToken() {
		when(worldsService.getWorldsMatches(null, "KESPA")).thenReturn(page(match("base-1"), null, null));

		leagueMatchService.syncMatchesWithoutTeamMetadata("KESPA");

		verify(worldsService, times(1)).getWorldsMatches(null, "KESPA");
		verify(worldsService, org.mockito.Mockito.never())
				.getWorldsMatches(org.mockito.ArgumentMatchers.argThat(token -> token != null), org.mockito.ArgumentMatchers.eq("KESPA"));
	}

	@Test
	@DisplayName("newer 토큰이 같은 값을 반복해도 무한 루프에 빠지지 않는다")
	void syncMatches_stopsOnRepeatedToken() {
		when(worldsService.getWorldsMatches(null, "LPL")).thenReturn(page(match("base-1"), "loop", null));
		when(worldsService.getWorldsMatches("loop", "LPL")).thenReturn(page(match("future-1"), "loop", null));

		leagueMatchService.syncMatchesWithoutTeamMetadata("LPL");

		verify(worldsService, times(1)).getWorldsMatches("loop", "LPL");
	}

	@Test
	@DisplayName("업스트림 bestOf 가 DTO→엔티티로 이어진다")
	void convertToEntity_carriesBestOf() {
		MatchResultDto dto = match("m1");
		dto.setBestOf(5);

		var entity = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
				leagueMatchService, "convertToEntity", dto, "LCK");

		assertThat(entity).isNotNull();
		assertThat(((com.toy.nar.app.lolesports.repository.LeagueMatch) entity).getBestOf()).isEqualTo(5);
	}

	private MatchResponseWrapper page(MatchResultDto match, String newerToken, String olderToken) {
		return MatchResponseWrapper.builder()
				.matches(List.of(match))
				.nextPageToken(olderToken)
				.newerPageToken(newerToken)
				.build();
	}

	private MatchResultDto match(String id) {
		return MatchResultDto.builder()
				.matchId(id)
				.leagueName("LCK")
				.matchTitle("플레이오프 | T1 vs GEN")
				.matchDate("2026-09-13T08:00:00Z")
				.state("unstarted")
				.blueTeam(MatchResultDto.TeamInfo.builder().code("T1").name("T1").wins(0).build())
				.redTeam(MatchResultDto.TeamInfo.builder().code("GEN").name("Gen.G").wins(0).build())
				.sets(List.of())
				.build();
	}
}
