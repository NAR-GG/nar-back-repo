package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.participant.entity.Player;
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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 구독 추가 네이티브 쿼리({@code INSERT IGNORE})를 실제 MySQL 8 에서 검증한다.
 * {@code INSERT IGNORE} 는 MySQL 전용 문법이고 "중복이면 예외가 아니라 무시" 라는 핵심 성질이
 * JPQL 이나 mock 으로는 확인되지 않아 MySQL 대상 테스트가 필요하다.
 *
 * <p>로컬 dev MySQL(docker-compose, 3308)의 격리 스키마 nar_favorite_player_test 에
 * ddl-auto=create-drop 으로 실행한다.
 * 사전 준비(최초 1회): CREATE DATABASE nar_favorite_player_test; GRANT ALL ON nar_favorite_player_test.* TO 'nar_id'@'%';
 * 실행: ./gradlew test -Ddataintegrity.local.enabled=true --tests "...MemberFavoritePlayerInsertIgnoreMySqlIntegrationTest"
 */
@EnabledIfSystemProperty(named = "dataintegrity.local.enabled", matches = "true")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberFavoritePlayerInsertIgnoreMySqlIntegrationTest {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:mysql://localhost:3308/nar_favorite_player_test?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
		registry.add("spring.datasource.username", () -> "nar_id");
		registry.add("spring.datasource.password", () -> "nar_pw");
		registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.flyway.enabled", () -> "false");
	}

	@Autowired
	private MemberFavoritePlayerRepository repository;

	@Autowired
	private TestEntityManager em;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long memberId;
	private Long playerId;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("DELETE FROM member_favorite_player");

		Member member = em.persist(Member.builder()
				.name("용맹한바론")
				.tag("0000")
				.email("insert-ignore@example.com")
				.build());
		Player player = em.persist(Player.builder().name("Faker").imageUrl("faker.png").build());
		em.flush();

		memberId = member.getId();
		playerId = player.getId();
	}

	@Test
	@DisplayName("같은 (member, player) 를 두 번 넣어도 예외 없이 1행만 남는다")
	void insertIgnoreIsIdempotent() {
		repository.insertIgnore(memberId, playerId, LocalDateTime.now());
		em.flush();
		em.clear();

		assertThatCode(() -> {
			repository.insertIgnore(memberId, playerId, LocalDateTime.now());
			em.flush();
		}).doesNotThrowAnyException();

		Integer rows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM member_favorite_player WHERE member_id = ? AND player_id = ?",
				Integer.class, memberId, playerId);
		assertThat(rows).isEqualTo(1);
	}

	@Test
	@DisplayName("먼저 넣은 행의 created_at 이 재호출로 덮이지 않는다")
	void insertIgnoreKeepsOriginalCreatedAt() {
		LocalDateTime first = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
		repository.insertIgnore(memberId, playerId, first);
		em.flush();
		em.clear();

		repository.insertIgnore(memberId, playerId, LocalDateTime.of(2026, 8, 15, 10, 58, 19));
		em.flush();

		LocalDateTime stored = jdbcTemplate.queryForObject(
				"SELECT created_at FROM member_favorite_player WHERE member_id = ? AND player_id = ?",
				LocalDateTime.class, memberId, playerId);
		assertThat(stored).isEqualTo(first);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	// 엔티티는 도메인 전체를 스캔한다(participant → game 처럼 서로 물려 있어 일부만 담으면 매핑이 깨진다).
	// 리포지토리 스캔만 member 로 좁혀 Elasticsearch 리포지토리를 피한다.
	@EntityScan(basePackages = "com.toy.nar.domain")
	@EnableJpaRepositories(basePackageClasses = MemberFavoritePlayerRepository.class)
	static class TestJpaConfiguration {
	}
}
