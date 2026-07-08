package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.toy.nar.domain.game.entity.League;
import com.toy.nar.domain.game.entity.LeagueTeam;
import com.toy.nar.domain.participant.entity.Team;

// 백오피스 검색 쿼리(searchForBackoffice) 검증. H2 격리 실행(마이그레이션은 MySQL 전용이라 엔티티 기반 스키마 사용).
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class TeamRepositorySearchTest {

	@Autowired
	private TeamRepository teamRepository;

	@PersistenceContext
	private EntityManager em;

	@Test
	@DisplayName("q가 null이면 전체, 값이 있으면 팀명·코드 부분일치(대소문자 무시)로 검색한다")
	void searchForBackoffice_matchesNameOrCode() {
		teamRepository.save(Team.builder().name("Gen.G").code("GEN").build());
		teamRepository.save(Team.builder().name("Dplus KIA").code("DK").build());
		teamRepository.save(Team.builder().name("T1").code("T1").build());

		// null → 전체
		assertThat(teamRepository.searchForBackoffice(null, null, PageRequest.of(0, 10)).getTotalElements())
				.isEqualTo(3);

		// 팀명 부분일치(소문자 입력도 매칭)
		List<String> byName = teamRepository.searchForBackoffice("gen", null, PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(byName).containsExactly("Gen.G");

		// 코드 부분일치
		List<String> byCode = teamRepository.searchForBackoffice("dk", null, PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(byCode).containsExactly("Dplus KIA");

		// 매칭 없음
		Page<Team> none = teamRepository.searchForBackoffice("zzz", null, PageRequest.of(0, 10));
		assertThat(none.getTotalElements()).isZero();
	}

	@Test
	@DisplayName("league을 주면 해당 리그(LeagueTeam)에 속한 팀만, q와 함께도 동작한다")
	void searchForBackoffice_filtersByLeague() {
		Team genG = teamRepository.save(Team.builder().name("Gen.G").code("GEN").build());
		Team t1 = teamRepository.save(Team.builder().name("T1").code("T1").build());
		Team weibo = teamRepository.save(Team.builder().name("Weibo Gaming").code("WBG").build());

		League lck = em.merge(League.builder().leagueName("LCK").seasonYear(2025)
				.seasonSplit("Spring").isPlayoffs(false).build());
		League lpl = em.merge(League.builder().leagueName("LPL").seasonYear(2025)
				.seasonSplit("Spring").isPlayoffs(false).build());
		em.persist(LeagueTeam.builder().league(lck).team(genG).build());
		em.persist(LeagueTeam.builder().league(lck).team(t1).build());
		em.persist(LeagueTeam.builder().league(lpl).team(weibo).build());
		em.flush();
		em.clear();

		// LCK 소속 팀만
		List<String> lckTeams = teamRepository.searchForBackoffice(null, "LCK", PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(lckTeams).containsExactlyInAnyOrder("Gen.G", "T1");

		// LCK + q 결합
		List<String> lckGen = teamRepository.searchForBackoffice("gen", "LCK", PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(lckGen).containsExactly("Gen.G");

		// 소속 팀 없는 리그
		assertThat(teamRepository.searchForBackoffice(null, "LEC", PageRequest.of(0, 10)).getTotalElements())
				.isZero();
	}
}
