package com.toy.nar.app.mobile.player;

import com.toy.nar.app.mobile.player.dto.PlayerSoloRankResponse;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;
import com.toy.nar.domain.participant.repository.PlayerPageQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MobilePlayerPageServiceTest {

	@Test
	void 스위스는_2승이면_진출전_2패면_탈락전이다() {
		assertThat(MobilePlayerPageService.stake("스위스", new int[]{2, 1})).isEqualTo("이기면 8강 진출");
		assertThat(MobilePlayerPageService.stake("스위스", new int[]{2, 2})).isEqualTo("이기면 8강 진출");
		assertThat(MobilePlayerPageService.stake("스위스", new int[]{1, 2})).isEqualTo("지면 탈락");
		assertThat(MobilePlayerPageService.stake("스위스", new int[]{1, 0})).isNull();
		assertThat(MobilePlayerPageService.stake("스위스", null)).isNull();
		assertThat(MobilePlayerPageService.stake("4강", null)).isEqualTo("이기면 결승 진출");
		assertThat(MobilePlayerPageService.stake("플레이-인", null)).isNull();
	}

	@Test
	void Riot_티어는_크롤링과_같은_모양으로_적는다() {
		assertThat(MobilePlayerPageService.tierLabel("CHALLENGER", "I", 1968)).isEqualTo("Ch 1,968LP");
		assertThat(MobilePlayerPageService.tierLabel("GRANDMASTER", "I", 812)).isEqualTo("GM 812LP");
		assertThat(MobilePlayerPageService.tierLabel("MASTER", "I", 0)).isEqualTo("Master 0LP");
		assertThat(MobilePlayerPageService.tierLabel("DIAMOND", "II", 40)).isEqualTo("Diamond II");
		assertThat(MobilePlayerPageService.tierLabel(null, null, null)).isNull();
	}

	@Test
	void 크롤링_티어는_riotId_접두로_맞춘다() {
		List<Map<String, Object>> crawled = List.of(
				Map.of("riotId", "Hide on bush#KR1", "tier", "Ch 2,256LP"),
				Map.of("riotId", "Hide in bush#NYKR Unranked", "tier", "Diamond I"));

		assertThat(MobilePlayerPageService.tier(crawled, "hide on bush#KR1")).isEqualTo("Ch 2,256LP");
		assertThat(MobilePlayerPageService.tier(crawled, "Hide in bush#NYKR")).isEqualTo("Diamond I");
		assertThat(MobilePlayerPageService.tier(crawled, "Hide in bush#NY")).isNull();
		assertThat(MobilePlayerPageService.region("NA1")).isEqualTo("NA");
		assertThat(MobilePlayerPageService.region("KR")).isEqualTo("KR");
	}

	@Test
	void 최근_전적은_오래된_순_결과와_평균을_낸다() {
		// 최신순으로 들어온다: 승(10/2/5) → 패(0/4/1)
		List<PlayerSoloRankGame> games = List.of(game(true, 10, 2, 5, 1800), game(false, 0, 4, 1, 1200));

		PlayerSoloRankResponse.Recent recent = MobilePlayerPageService.recent(games, 14);

		assertThat(recent.results()).containsExactly(false, true);
		assertThat(recent.wins()).isEqualTo(1);
		assertThat(recent.losses()).isEqualTo(1);
		assertThat(recent.avgKills()).isEqualTo(5.0);
		assertThat(recent.kda()).isEqualTo(2.7); // (10+6)/6
		assertThat(recent.gamesPerDay()).isEqualTo(2.0);
		assertThat(recent.avgDurationSeconds()).isEqualTo(1500);
	}

	@Test
	void 리뷰_상대팀은_세트_진영이_아니라_팀_코드로_고른다() {
		// T1 이 매치에서 blue 로 등록됐지만 이 세트는 red 진영
		PlayerPageQueryRepository.ReviewRow row = new PlayerPageQueryRepository.ReviewRow(
				1L, "g1", 3, 5, "굿", "Red", "m1", "스위스 | T1 vs DK", "T1", "DK", LocalDateTime.now());

		assertThat(MobilePlayerPageService.opponent(row, "T1")).isEqualTo("DK");
		assertThat(MobilePlayerPageService.opponent(row, null)).isEqualTo("T1");
	}

	@Test
	void 스테이지는_스위스면_팀_기준_라운드를_붙인다() {
		Map<String, Integer> rounds = Map.of("m3", 3);

		assertThat(MobilePlayerPageService.stage("m3", "스위스 | T1 vs IG", rounds)).isEqualTo("스위스 3라운드");
		assertThat(MobilePlayerPageService.stage("m9", "스위스 | T1 vs IG", rounds)).isEqualTo("스위스");
		assertThat(MobilePlayerPageService.stage("k1", "8강 | T1 vs GEN", rounds)).isEqualTo("8강");
		assertThat(MobilePlayerPageService.stage("k1", null, rounds)).isNull();
	}

	private static PlayerSoloRankGame game(boolean win, int kills, int deaths, int assists, int duration) {
		PlayerSoloRankGame game = new PlayerSoloRankGame(Player.builder().name("p").build(), "KR_1", null, LocalDateTime.now());
		ReflectionTestUtils.setField(game, "win", win);
		ReflectionTestUtils.setField(game, "kills", kills);
		ReflectionTestUtils.setField(game, "deaths", deaths);
		ReflectionTestUtils.setField(game, "assists", assists);
		ReflectionTestUtils.setField(game, "durationSeconds", duration);
		return game;
	}
}
