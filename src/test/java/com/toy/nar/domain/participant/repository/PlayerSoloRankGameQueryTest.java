package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.toy.nar.domain.participant.entity.Champion;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.PlayerRiotAccount;
import com.toy.nar.domain.participant.entity.PlayerRiotAccountLiveStatus;
import com.toy.nar.domain.participant.entity.PlayerSoloRankGame;

/**
 * 홈 솔랭 카드 쿼리가 실제로 파싱·실행되는지 본다 — 엔티티 조인(ON), fetch, 조건부 UPDATE.
 * Flyway 는 MySQL 전용이라 끄고 엔티티로 스키마를 만든다(LeagueMatchStandingsQueryTest 와 같은 방식).
 */
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
class PlayerSoloRankGameQueryTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private PlayerSoloRankGameRepository repository;

	private final LocalDateTime now = LocalDateTime.of(2026, 9, 25, 21, 0);
	private Player faker;
	private Player chovy;
	private Player zeus;
	private Champion ahri;

	@BeforeEach
	void setUp() {
		faker = em.persist(Player.builder().name("Faker").build());
		chovy = em.persist(Player.builder().name("Chovy").build());
		zeus = em.persist(Player.builder().name("Zeus").build());
		ahri = em.persist(Champion.builder().championNameKr("아리").championNameEn("Ahri").imageUrl("ahri.png").build());
	}

	private void account(Player player, PlayerRiotAccountLiveStatus status, String gameId, LocalDateTime checkedAt) {
		em.persist(PlayerRiotAccount.builder()
				.player(player).riotId(player.getName() + "#KR1").gameName(player.getName()).tagLine("KR1")
				.platform("kr").puuid("puuid-" + player.getName()).enabled(true).primaryAccount(true)
				.liveStatus(status).lastCheckedMatchId(gameId).lastMatchCheckedAt(checkedAt)
				.build());
	}

	private void game(Player player, String gameId, LocalDateTime detectedAt) {
		em.persist(new PlayerSoloRankGame(player, gameId, ahri, detectedAt));
	}

	@Test
	void 라이브는_계정이_보는_게임과_맞물리고_최근에_확인된_것만() {
		account(faker, PlayerRiotAccountLiveStatus.IN_RANKED_SOLO, "g1", now.minusMinutes(1));
		game(faker, "g1", now.minusMinutes(20));
		game(faker, "g0", now.minusHours(2)); // 같은 선수의 지난 판 — 계정이 안 보고 있다
		account(chovy, PlayerRiotAccountLiveStatus.IN_RANKED_SOLO, "g2", now.minusHours(1)); // 모니터가 멈춘 계정
		game(chovy, "g2", now.minusHours(1));
		account(zeus, PlayerRiotAccountLiveStatus.OFFLINE, "g3", now.minusMinutes(1));
		game(zeus, "g3", now.minusMinutes(30));
		em.flush();
		em.clear();

		List<PlayerSoloRankGame> live = repository.findLive(
				List.of(faker.getId(), chovy.getId(), zeus.getId()),
				PlayerRiotAccountLiveStatus.IN_RANKED_SOLO, now.minusMinutes(15));

		assertThat(live).extracting(PlayerSoloRankGame::getGameId).containsExactly("g1");
		assertThat(live.get(0).getChampion().getChampionNameKr()).isEqualTo("아리");
	}

	@Test
	void 시작_시각은_비어_있을_때만_채운다() {
		game(faker, "g1", now.minusMinutes(20));
		em.flush();

		assertThat(repository.fillStartedAt(faker.getId(), "g1", now.minusMinutes(19))).isEqualTo(1);
		assertThat(repository.fillStartedAt(faker.getId(), "g1", now.minusMinutes(5))).isZero();

		PlayerSoloRankGame row = repository.findAll().get(0);
		assertThat(row.getGameStartedAt()).isEqualTo(now.minusMinutes(19));
	}

	@Test
	void 끝난_게임은_범위_안에서_최근_종료순이고_결과가_실린다() {
		game(faker, "old", now.minusHours(30));
		game(faker, "g1", now.minusHours(3));
		game(faker, "g2", now.minusHours(1));
		game(chovy, "c1", now.minusHours(2));
		game(chovy, "running", now.minusMinutes(10)); // 아직 결과 없음
		em.flush();
		repository.recordResult(faker.getId(), "old", now.minusHours(30), now.minusHours(29), 1800, true, 1, 1, 1);
		repository.recordResult(faker.getId(), "g1", now.minusHours(3), now.minusMinutes(150), 1800, false, 2, 5, 3);
		repository.recordResult(faker.getId(), "g2", now.minusHours(1), now.minusMinutes(30), 1800, true, 12, 1, 9);
		repository.recordResult(chovy.getId(), "c1", null, now.minusMinutes(90), 1500, true, 7, 0, 4);

		LocalDateTime endedSince = now.minusHours(24);
		List<PlayerSoloRankGame> finished = repository.findFinishedSince(
				List.of(faker.getId(), chovy.getId()), endedSince.minusHours(3), endedSince);

		assertThat(finished).extracting(PlayerSoloRankGame::getGameId).containsExactly("g2", "c1", "g1");
		PlayerSoloRankGame latest = finished.get(0);
		assertThat(latest.getWin()).isTrue();
		assertThat(latest.getKills()).isEqualTo(12);
		assertThat(latest.getDurationSeconds()).isEqualTo(1800);
		// match-v5 시작 시각이 없으면 기존 값을 지우지 않는다
		assertThat(finished.get(1).getGameStartedAt()).isNull();
	}
}
