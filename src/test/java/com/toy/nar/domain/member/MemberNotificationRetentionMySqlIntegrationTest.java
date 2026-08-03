package com.toy.nar.domain.member;

import com.toy.nar.domain.member.repository.MemberNotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보존 정리 쿼리({@code deleteOlderThanByType})를 실제 MySQL 8 + Hibernate 6.6 에서 검증한다.
 *
 * <p>JPQL 이 DELETE 에 LIMIT 을 못 붙여 네이티브 쿼리를 쓰는데, {@code LIMIT :chunkSize} 처럼
 * LIMIT 절에 바인딩 파라미터를 넣는 건 H2 에서 재현되지 않아 단위 테스트로 잡히지 않는다.
 * 이 쿼리가 깨지면 새벽 정리 잡이 통째로 실패하므로 실제 MySQL 로 확인한다.
 *
 * <p>로컬 dev MySQL(docker-compose, 3308)의 격리 스키마에 ddl-auto=create-drop 으로 실행한다.
 * 사전 준비(최초 1회):
 * {@code CREATE DATABASE nar_notification_retention_test; GRANT ALL ON nar_notification_retention_test.* TO 'nar_id'@'%';}
 * 실행: {@code ./gradlew test -Ddataintegrity.local.enabled=true --tests "...MemberNotificationRetentionMySqlIntegrationTest"}
 */
@EnabledIfSystemProperty(named = "dataintegrity.local.enabled", matches = "true")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberNotificationRetentionMySqlIntegrationTest {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:mysql://localhost:3308/nar_notification_retention_test"
						+ "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
		registry.add("spring.datasource.username", () -> "nar_id");
		registry.add("spring.datasource.password", () -> "nar_pw");
		registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.flyway.enabled", () -> "false");
	}

	/**
	 * 전체 앱을 띄우면 {@code @EnableElasticsearchRepositories} 가 딸려와 elasticsearchTemplate 이 없어
	 * 컨텍스트가 죽는다 — 저장소의 다른 리포지토리 통합 테스트와 같이 리포지토리 스캔을 member 로 좁힌다.
	 * 엔티티는 도메인 간 매핑이 서로 물려 있어 전체를 스캔한다.
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EntityScan(basePackages = "com.toy.nar.domain")
	@EnableJpaRepositories(basePackageClasses = MemberNotificationRepository.class)
	static class TestJpaConfiguration {
	}

	private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 7, 28, 0, 0);

	@Autowired
	private MemberNotificationRepository repository;

	@Autowired
	private EntityManager em;

	// createdAt 이 엔티티 생성자에서 now() 로 고정돼 과거 데이터를 만들 수 없어 네이티브로 심는다.
	@BeforeEach
	void seed() {
		em.createNativeQuery("DELETE FROM member_notification").executeUpdate();
		em.createNativeQuery("DELETE FROM member").executeUpdate();
		em.createNativeQuery(
				"INSERT INTO member (id, name, tag, role, created_at) VALUES (1, 'tester', 'T1', 'USER', '2026-07-01')")
				.executeUpdate();

		insert("LIVE_EVENT", "2026-07-01");
		insert("LIVE_EVENT", "2026-07-02");
		insert("LIVE_EVENT", "2026-07-03");
		insert("LIVE_EVENT", "2026-08-04");   // 컷오프 이후 — 남아야 한다
		insert("SET_START", "2026-07-01");    // 다른 타입 — 남아야 한다
		em.flush();
		em.clear();
	}

	private void insert(String type, String createdAt) {
		em.createNativeQuery("INSERT INTO member_notification (member_id, type, title, created_at) "
						+ "VALUES (1, :type, 't', :createdAt)")
				.setParameter("type", type)
				.setParameter("createdAt", createdAt)
				.executeUpdate();
	}

	@Test
	void 청크_크기만큼만_지우고_타입과_컷오프를_지킨다() {
		int first = repository.deleteOlderThanByType("LIVE_EVENT", CUTOFF, 2);
		assertThat(first).isEqualTo(2);

		int second = repository.deleteOlderThanByType("LIVE_EVENT", CUTOFF, 2);
		assertThat(second).isEqualTo(1);

		int third = repository.deleteOlderThanByType("LIVE_EVENT", CUTOFF, 2);
		assertThat(third).isZero();

		assertThat(remaining("LIVE_EVENT")).isEqualTo(1);  // 2026-08-04
		assertThat(remaining("SET_START")).isEqualTo(1);   // 타입이 달라 안 지워진다
	}

	private long remaining(String type) {
		return ((Number) em.createNativeQuery(
						"SELECT COUNT(*) FROM member_notification WHERE type = :type")
				.setParameter("type", type)
				.getSingleResult()).longValue();
	}
}
