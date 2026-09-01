package com.toy.nar.app.mobile.match;

import com.toy.nar.app.lolesports.live.LiveStateQueryService;
import com.toy.nar.app.lolesports.live.RuneMetadataResolver;
import com.toy.nar.app.lolesports.live.dto.LiveObjectEventResponse;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.entity.LiveGameMapping;
import com.toy.nar.app.lolesports.live.repository.LiveGameMappingRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import com.toy.nar.app.lolesports.live.dto.LiveParticipantState;
import com.toy.nar.app.mobile.match.dto.LiveBanRow;
import com.toy.nar.app.mobile.match.dto.LiveGameChampionsResponse;
import com.toy.nar.domain.game.repository.BanRepository;
import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.repository.ChampionRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobileLiveGameServiceTest {

	private final LiveStateQueryService liveStateQueryService = mock(LiveStateQueryService.class);
	private final LiveGameObjectEventRepository objectEventRepository = mock(LiveGameObjectEventRepository.class);
	private final LiveGameMinuteSnapshotRepository minuteSnapshotRepository = mock(LiveGameMinuteSnapshotRepository.class);
	private final ChampionRepository championRepository = mock(ChampionRepository.class);
	private final TeamRepository teamRepository = mock(TeamRepository.class);
	private final LiveGameMappingRepository liveGameMappingRepository = mock(LiveGameMappingRepository.class);
	private final BanRepository banRepository = mock(BanRepository.class);
	private final RuneMetadataResolver runeMetadataResolver = mock(RuneMetadataResolver.class);

	private final MobileLiveGameService service = new MobileLiveGameService(
			liveStateQueryService,
			objectEventRepository,
			minuteSnapshotRepository,
			championRepository,
			teamRepository,
			liveGameMappingRepository,
			banRepository,
			runeMetadataResolver);

	@BeforeEach
	void stubRuneIcons() {
		// 룬 아이콘은 ddragon 로드가 필요한 별개 관심사 — 이 테스트에서는 빈 값으로 고정한다.
		when(runeMetadataResolver.resolveRuneIcons(any()))
				.thenReturn(new RuneMetadataResolver.RuneIcons(null, null));
	}

	private LiveGameState stateWith(String blueTeam, String redTeam) {
		// 참가자(픽)는 이 테스트의 관심사가 아니므로 빈 목록 — 밴 채움만 검증한다.
		return new LiveGameState("LIVE_G", "M1", "LCK", blueTeam, redTeam, null, null, List.of(), List.of());
	}

	private LiveBanRow banRow(String side, String championName, String imageUrl) {
		// 익명 구현 — mock 안에서 when() 을 호출하면 외부 stubbing 과 중첩돼 깨지므로 직접 구현.
		return new LiveBanRow() {
			@Override
			public String getSide() {
				return side;
			}

			@Override
			public String getChampionName() {
				return championName;
			}

			@Override
			public String getImageUrl() {
				return imageUrl;
			}
		};
	}

	@Test
	void getChampions_reconcile된_경기는_배치_밴을_진영별로_채운다() {
		when(liveStateQueryService.getLatestState("LIVE_G"))
				.thenReturn(Optional.of(stateWith("T1", "Gen.G")));

		LiveGameMapping mapping = mock(LiveGameMapping.class);
		when(mapping.getInternalGameId()).thenReturn(100L);
		when(liveGameMappingRepository.findByLiveGameId("LIVE_G")).thenReturn(Optional.of(mapping));

		when(banRepository.findLiveBanRowsByGameId(100L)).thenReturn(List.of(
				banRow("BLUE", "Aatrox", "url/aatrox"),
				banRow("BLUE", "Karma", "url/karma"),
				banRow("RED", "Yasuo", "url/yasuo")));

		LiveGameChampionsResponse response = service.getChampions("LIVE_G");

		assertThat(response.blueTeam().teamName()).isEqualTo("T1");
		assertThat(response.blueTeam().bans())
				.extracting(LiveGameChampionsResponse.Ban::championName)
				.containsExactly("Aatrox", "Karma");
		assertThat(response.blueTeam().bans().get(0).championImageUrl()).isEqualTo("url/aatrox");

		assertThat(response.redTeam().teamName()).isEqualTo("Gen.G");
		assertThat(response.redTeam().bans())
				.extracting(LiveGameChampionsResponse.Ban::championName)
				.containsExactly("Yasuo");
	}

	private LiveParticipantState pick(String side, String role, String championName, String playerName) {
		return new LiveParticipantState(
				1, side, role, playerName, null, championName,
				null, null, null, null, null, null, null, null,
				List.of(), List.of(), List.of(), null, null, null, List.of());
	}

	@Test
	void getChampions_픽은_고화질_로딩이미지를_쓰고_없으면_정사각으로_폴백한다() {
		LiveGameState state = new LiveGameState("LIVE_G", "M1", "LCK", "T1", "Gen.G", null, null,
				List.of(pick("BLUE", "top", "Renekton", "Kiin"),
						pick("RED", "mid", "Azir", "Chovy")),
				List.of());
		when(liveStateQueryService.getLatestState("LIVE_G")).thenReturn(Optional.of(state));
		when(liveGameMappingRepository.findByLiveGameId("LIVE_G")).thenReturn(Optional.empty());

		// loading_image_url 있는 챔피언 → 고화질 splash 사용
		when(championRepository.findByChampionNameEn("Renekton")).thenReturn(Optional.of(Champion.builder()
				.championNameKr("레넥톤").championNameEn("Renekton")
				.imageUrl("sq/renekton.png")
				.loadingImageUrl("https://cdn.communitydragon.org/latest/champion/58/splash-art/centered")
				.build()));
		// loading_image_url 없는 챔피언 → 정사각 아이콘으로 폴백
		when(championRepository.findByChampionNameEn("Azir")).thenReturn(Optional.of(Champion.builder()
				.championNameKr("아지르").championNameEn("Azir")
				.imageUrl("sq/azir.png")
				.build()));

		LiveGameChampionsResponse response = service.getChampions("LIVE_G");

		assertThat(response.blueTeam().picks().get(0).championImageUrl())
				.isEqualTo("https://cdn.communitydragon.org/latest/champion/58/splash-art/centered");
		assertThat(response.redTeam().picks().get(0).championImageUrl()).isEqualTo("sq/azir.png");
	}

	private LiveObjectEventResponse objectEvent(String side, String type, String subType, int order) {
		return new LiveObjectEventResponse(side, type, subType, order, order, null);
	}

	@Test
	void getChampions_오브젝트는_장로용을_드래곤에서_분리해_센다() {
		// value_after 는 장로를 드래곤 카운터에 포함해 센다(1,2,3). 그대로 쓰면 "드래곤 3"이 되어 틀린다.
		LiveGameState state = new LiveGameState("LIVE_G", "M1", "LCK", "T1", "Gen.G", null, null,
				List.of(),
				List.of(
						objectEvent("Blue", "DRAGON", "infernal", 1),
						objectEvent("Blue", "DRAGON", "ocean", 2),
						objectEvent("Blue", "DRAGON", "elder", 3),
						objectEvent("Blue", "BARON", null, 1),
						objectEvent("Blue", "TOWER", null, 1),
						objectEvent("Blue", "TOWER", null, 2),
						objectEvent("Blue", "INHIBITOR", null, 1),
						objectEvent("Blue", "KILL", "Azir", 1),
						objectEvent("Red", "DRAGON", "cloud", 1)));
		when(liveStateQueryService.getLatestState("LIVE_G")).thenReturn(Optional.of(state));
		when(liveGameMappingRepository.findByLiveGameId("LIVE_G")).thenReturn(Optional.empty());

		LiveGameChampionsResponse.TeamObjectives blue = service.getChampions("LIVE_G").objectives().blueTeam();

		assertThat(blue.dragons()).isEqualTo(2);
		assertThat(blue.dragonTypes()).containsExactly("infernal", "ocean");
		assertThat(blue.elders()).isEqualTo(1);
		assertThat(blue.barons()).isEqualTo(1);
		assertThat(blue.towers()).isEqualTo(2);
		assertThat(blue.inhibitors()).isEqualTo(1);

		LiveGameChampionsResponse.TeamObjectives red = service.getChampions("LIVE_G").objectives().redTeam();
		assertThat(red.dragons()).isEqualTo(1);
		assertThat(red.elders()).isZero();
		assertThat(red.towers()).isZero();
	}

	@Test
	void getChampions_팀_요약은_참가자_값을_합산하고_null_은_건너뛴다() {
		LiveParticipantState scored = new LiveParticipantState(
				1, "BLUE", "top", "Kiin", null, "Renekton",
				17, 3, 1, 4, 12_000, 250, 0.5, 0.2,
				List.of(), List.of(), List.of(), null, null, null, List.of());
		// CS·골드가 아직 안 채워진 첫 버킷 참가자 — 합산에서 건너뛰어야 한다(NPE 없이).
		LiveParticipantState empty = pick("BLUE", "mid", "Azir", "Faker");
		LiveGameState state = new LiveGameState("LIVE_G", "M1", "LCK", "T1", "Gen.G", null, null,
				List.of(scored, empty), List.of());
		when(liveStateQueryService.getLatestState("LIVE_G")).thenReturn(Optional.of(state));
		when(liveGameMappingRepository.findByLiveGameId("LIVE_G")).thenReturn(Optional.empty());

		LiveGameChampionsResponse.TeamSummary summary = service.getChampions("LIVE_G").blueTeam().summary();

		assertThat(summary.kills()).isEqualTo(3);
		assertThat(summary.creepScore()).isEqualTo(250);
		assertThat(summary.totalGoldEarned()).isEqualTo(12_000);
	}

	@Test
	void getChampions_reconcile_안된_경기는_밴이_빈_목록이다() {
		when(liveStateQueryService.getLatestState("LIVE_G"))
				.thenReturn(Optional.of(stateWith("T1", "Gen.G")));
		when(liveGameMappingRepository.findByLiveGameId("LIVE_G")).thenReturn(Optional.empty());

		LiveGameChampionsResponse response = service.getChampions("LIVE_G");

		assertThat(response.blueTeam().bans()).isEmpty();
		assertThat(response.redTeam().bans()).isEmpty();
	}
}
