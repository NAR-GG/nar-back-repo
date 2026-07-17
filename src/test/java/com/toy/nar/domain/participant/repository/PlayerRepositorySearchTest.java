package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.entity.LeagueTeam;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.entity.Team;

// 백오피스 선수 검색의 리그 필터 검증. 기준 = 현재 소속팀(current_team)의 LeagueTeam 등록.
// (과거에는 경기 출전 이력 EXISTS 스캔이라 참가기록 전체를 훑어 느렸다 — 소속팀 기준으로 교체.)
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class PlayerRepositorySearchTest {

	@Autowired
	private PlayerRepository playerRepository;

	@Autowired
	private TeamRepository teamRepository;

	@PersistenceContext
	private EntityManager em;

	@Test
	@DisplayName("league를 주면 현재 소속팀이 그 리그(LeagueTeam)에 등록된 선수만 나온다")
	void searchForBackoffice_filtersByCurrentTeamLeague() {
		Team genG = teamRepository.save(Team.builder().name("Gen.G").code("GEN").build());
		Team weibo = teamRepository.save(Team.builder().name("Weibo Gaming").code("WBG").build());

		League lck = em.merge(League.builder().leagueName("LCK").seasonYear(2025)
				.seasonSplit("Spring").isPlayoffs(false).build());
		League lpl = em.merge(League.builder().leagueName("LPL").seasonYear(2025)
				.seasonSplit("Spring").isPlayoffs(false).build());
		em.persist(LeagueTeam.builder().league(lck).team(genG).build());
		em.persist(LeagueTeam.builder().league(lpl).team(weibo).build());

		Player chovy = Player.builder().name("Chovy").build();
		chovy.changeCurrentTeam(genG);
		playerRepository.save(chovy);

		Player xiaohu = Player.builder().name("Xiaohu").build();
		xiaohu.changeCurrentTeam(weibo);
		playerRepository.save(xiaohu);

		Player freeAgent = Player.builder().name("FreeAgent").build(); // 무소속
		playerRepository.save(freeAgent);

		em.flush();
		em.clear();

		// LCK 소속팀 선수만
		List<String> lckPlayers = playerRepository.searchForBackoffice(null, "LCK", PageRequest.of(0, 10))
				.map(Player::getName).getContent();
		assertThat(lckPlayers).containsExactly("Chovy");

		// league 없으면 전체(무소속 포함)
		assertThat(playerRepository.searchForBackoffice(null, null, PageRequest.of(0, 10)).getTotalElements())
				.isEqualTo(3);

		// q 결합
		List<String> lckCho = playerRepository.searchForBackoffice("cho", "LCK", PageRequest.of(0, 10))
				.map(Player::getName).getContent();
		assertThat(lckCho).containsExactly("Chovy");
	}
}
