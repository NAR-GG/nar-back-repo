package com.toy.nar.domain.member.repository;

import com.toy.nar.app.lolesports.repository.LeagueMatch;
import com.toy.nar.app.lolesports.repository.LeagueMatchRepository;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.entity.MemberMatchSubscription;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 백오피스 경기 구독 탭 네이티브 쿼리(GROUP BY 집계 + field/q 검색 분기)를 실제 MySQL 8 에서 검증한다.
 * 집계 정렬(구독자 수 desc)과 ONLY_FULL_GROUP_BY 하의 GROUP BY 컬럼 나열이 런타임에 깨지지 않음을 보장한다.
 * 로컬 dev MySQL(docker-compose, 3308)의 격리 스키마 nar_match_subscription_test 에 ddl-auto=create-drop 으로 실행한다.
 * 사전 준비(최초 1회, root 계정):
 * CREATE DATABASE nar_match_subscription_test; GRANT ALL ON nar_match_subscription_test.* TO 'nar_id'@'%';
 * 실행: ./gradlew test -Ddataintegrity.local.enabled=true --tests "...MemberMatchSubscriptionBackofficeMySqlIntegrationTest"
 */
@EnabledIfSystemProperty(named = "dataintegrity.local.enabled", matches = "true")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberMatchSubscriptionBackofficeMySqlIntegrationTest {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:mysql://localhost:3308/nar_match_subscription_test?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
		registry.add("spring.datasource.username", () -> "nar_id");
		registry.add("spring.datasource.password", () -> "nar_pw");
		registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.flyway.enabled", () -> "false");
	}

	@Autowired
	private MemberMatchSubscriptionRepository repository;

	@Autowired
	private LeagueMatchRepository matchRepository;

	@PersistenceContext
	private EntityManager em;

	@BeforeEach
	void seed() {
		// m-hot: 구독자 2명, m-cold: 1명, m-none: 0명(목록에서 빠져야 함).
		matchRepository.saveAll(List.of(
				match("m-hot", "LCK", LocalDateTime.of(2026, 7, 10, 9, 0), "T1", "GEN"),
				match("m-cold", "LCK", LocalDateTime.of(2026, 7, 11, 9, 0), "KT", "DK"),
				match("m-none", "LEC", LocalDateTime.of(2026, 7, 12, 9, 0), "G2", "FNC")));

		Member alice = new Member("앨리스", "1234", "a@nar.kr");
		Member bob = new Member("밥", "5678", "b@nar.kr");
		em.persist(alice);
		em.persist(bob);

		repository.saveAll(List.of(
				new MemberMatchSubscription(alice, "m-hot", true, true, true),
				new MemberMatchSubscription(bob, "m-hot", true, false, true),
				new MemberMatchSubscription(alice, "m-cold", false, true, true)));
		em.flush();
	}

	@Test
	@DisplayName("구독자가 있는 경기만 구독자 수 내림차순으로 내려준다")
	void listsOnlySubscribedMatchesOrderedByCount() {
		var page = repository.findSubscribedMatches(null, PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent()).extracting(v -> v.getMatchId() + ":" + v.getSubscriberCount())
				.containsExactly("m-hot:2", "m-cold:1");
	}

	@Test
	@DisplayName("q는 경기명·팀명 부분일치로 거른다")
	void searchMatchesTeamName() {
		var page = repository.findSubscribedMatches("gen", PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(v -> v.getMatchId()).containsExactly("m-hot");
	}

	@Test
	@DisplayName("경기 구독자 목록은 알림 토글 3종을 함께 내려준다")
	void listsSubscribersWithToggles() {
		var page = repository.findSubscribersByMatchId("m-hot", "nickname", null, PageRequest.of(0, 20));

		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent()).extracting(v -> v.getName() + "#" + v.getTag())
				.containsExactlyInAnyOrder("앨리스#1234", "밥#5678");
		assertThat(page.getContent()).filteredOn(v -> "밥".equals(v.getName()))
				.allMatch(v -> !v.getSetEndEnabled() && v.getSetStartEnabled());
	}

	@Test
	@DisplayName("field=nickname + q 로 구독자를 검색한다")
	void searchSubscribersByNickname() {
		var page = repository.findSubscribersByMatchId("m-hot", "nickname", "앨리스", PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(v -> v.getEmail()).containsExactly("a@nar.kr");
	}

	private static LeagueMatch match(String id, String league, LocalDateTime date, String blue, String red) {
		return LeagueMatch.builder()
				.id(id)
				.leagueName(league)
				.matchTitle(blue + " vs " + red)
				.matchDate(date)
				.state("completed")
				.blueTeamName(blue)
				.redTeamName(red)
				.build();
	}

	// NarApplication 을 끌어오면 @EnableElasticsearchRepositories 까지 딸려와 elasticsearchTemplate 이 없어 컨텍스트가 죽는다.
	// 리뷰 검색 테스트와 동일하게 JPA 만 담은 최소 설정을 쓴다.
	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EntityScan(basePackages = {
			"com.toy.nar.domain.member.entity",
			"com.toy.nar.domain.participant.entity",
			"com.toy.nar.domain.game.entity",
			"com.toy.nar.app.lolesports.repository"
	})
	@EnableJpaRepositories(basePackageClasses = {
			MemberMatchSubscriptionRepository.class,
			LeagueMatchRepository.class
	})
	static class TestJpaConfiguration {
	}
}
