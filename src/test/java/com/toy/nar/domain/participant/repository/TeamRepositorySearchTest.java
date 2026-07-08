package com.toy.nar.domain.participant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.toy.nar.domain.participant.entity.Team;

// 백오피스 검색 쿼리(searchForBackoffice) 검증. H2 격리 실행(마이그레이션은 MySQL 전용이라 엔티티 기반 스키마 사용).
@DataJpaTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class TeamRepositorySearchTest {

	@Autowired
	private TeamRepository teamRepository;

	@Test
	@DisplayName("q가 null이면 전체, 값이 있으면 팀명·코드 부분일치(대소문자 무시)로 검색한다")
	void searchForBackoffice_matchesNameOrCode() {
		teamRepository.save(Team.builder().name("Gen.G").code("GEN").build());
		teamRepository.save(Team.builder().name("Dplus KIA").code("DK").build());
		teamRepository.save(Team.builder().name("T1").code("T1").build());

		// null → 전체
		assertThat(teamRepository.searchForBackoffice(null, PageRequest.of(0, 10)).getTotalElements())
				.isEqualTo(3);

		// 팀명 부분일치(소문자 입력도 매칭)
		List<String> byName = teamRepository.searchForBackoffice("gen", PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(byName).containsExactly("Gen.G");

		// 코드 부분일치
		List<String> byCode = teamRepository.searchForBackoffice("dk", PageRequest.of(0, 10))
				.map(Team::getName).getContent();
		assertThat(byCode).containsExactly("Dplus KIA");

		// 매칭 없음
		Page<Team> none = teamRepository.searchForBackoffice("zzz", PageRequest.of(0, 10));
		assertThat(none.getTotalElements()).isZero();
	}
}
