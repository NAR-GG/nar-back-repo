package com.toy.nar.app.mobile.match;

import com.toy.nar.app.lolesports.live.LiveStateQueryService;
import com.toy.nar.app.lolesports.live.dto.LiveGameState;
import com.toy.nar.app.lolesports.live.entity.LiveGameMapping;
import com.toy.nar.app.lolesports.live.repository.LiveGameMappingRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameMinuteSnapshotRepository;
import com.toy.nar.app.lolesports.live.repository.LiveGameObjectEventRepository;
import com.toy.nar.app.mobile.match.dto.LiveBanRow;
import com.toy.nar.app.mobile.match.dto.LiveGameChampionsResponse;
import com.toy.nar.domain.game.repository.BanRepository;
import com.toy.nar.domain.participant.repository.ChampionRepository;
import com.toy.nar.domain.participant.repository.TeamRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

	private final MobileLiveGameService service = new MobileLiveGameService(
			liveStateQueryService,
			objectEventRepository,
			minuteSnapshotRepository,
			championRepository,
			teamRepository,
			liveGameMappingRepository,
			banRepository);

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
