package com.toy.nar.domain.member.repository;

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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구독 추가 upsert 를 실제 MySQL 8 에서 검증한다. 이 쿼리의 핵심 성질 세 가지는 mock 이나 H2 로는
 * 확인되지 않는다 — 중복을 삼키는가, 중복 <b>만</b> 삼키는가, 그리고 동시 요청에서 어떻게 깨지는가.
 *
 * <p>로컬 dev MySQL(docker-compose, 3308)의 격리 스키마 nar_favorite_player_test 에
 * ddl-auto=create-drop 으로 실행한다.
 * 사전 준비(최초 1회): CREATE DATABASE nar_favorite_player_test; GRANT ALL ON nar_favorite_player_test.* TO 'nar_id'@'%';
 * 실행: ./gradlew test -Ddataintegrity.local.enabled=true --tests "...MemberFavoritePlayerUpsertMySqlIntegrationTest"
 */
@EnabledIfSystemProperty(named = "dataintegrity.local.enabled", matches = "true")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 테스트를 트랜잭션으로 감싸지 않는다. 동시성 테스트가 별도 커넥션에서 같은 행을 노려야 하는데,
// 테스트 트랜잭션 안에만 있는 회원/선수는 그 커넥션에서 보이지 않아 FK 위반으로 죽는다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MemberFavoritePlayerUpsertMySqlIntegrationTest {

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

	private static final String INSERT_SQL = """
			INSERT INTO member_favorite_player (member_id, player_id, created_at)
			VALUES (?, ?, ?)
			ON DUPLICATE KEY UPDATE id = id
			""";

	@Autowired
	private MemberFavoritePlayerRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DataSource dataSource;

	private Long memberId;
	private Long playerId;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("DELETE FROM member_favorite_player");
		jdbcTemplate.execute("DELETE FROM member");
		jdbcTemplate.execute("DELETE FROM players");

		jdbcTemplate.update("""
				INSERT INTO member (name, tag, role, created_at, quiet_hours_enabled, quiet_start_time, quiet_end_time)
				VALUES ('용맹한바론', '0000', 'USER', NOW(), FALSE, '01:00:00', '08:00:00')
				""");
		jdbcTemplate.update("INSERT INTO players (player_name, image_url) VALUES ('Faker', 'faker.png')");

		memberId = jdbcTemplate.queryForObject("SELECT id FROM member LIMIT 1", Long.class);
		playerId = jdbcTemplate.queryForObject("SELECT player_id FROM players LIMIT 1", Long.class);
	}

	@Test
	@DisplayName("같은 (member, player) 를 두 번 넣어도 예외 없이 1행만 남는다")
	void upsertIsIdempotent() {
		repository.insertIfAbsent(memberId, playerId, LocalDateTime.now());

		assertThatCode(() -> repository.insertIfAbsent(memberId, playerId, LocalDateTime.now()))
				.doesNotThrowAnyException();

		assertThat(countRows()).isEqualTo(1);
	}

	@Test
	@DisplayName("먼저 넣은 행의 created_at 이 재호출로 덮이지 않는다 — 구독 시작 시각이 매 요청마다 리셋되면 안 된다")
	void upsertKeepsOriginalCreatedAt() {
		LocalDateTime first = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
		repository.insertIfAbsent(memberId, playerId, first);
		repository.insertIfAbsent(memberId, playerId, LocalDateTime.of(2026, 8, 15, 10, 58, 19));

		LocalDateTime stored = jdbcTemplate.queryForObject(
				"SELECT created_at FROM member_favorite_player WHERE member_id = ? AND player_id = ?",
				LocalDateTime.class, memberId, playerId);
		assertThat(stored).isEqualTo(first);
	}

	/**
	 * INSERT IGNORE 를 쓰면 안 되는 이유를 못 박는다. IGNORE 는 중복만이 아니라 FK 위반(1452)까지
	 * warning 으로 강등해 조용히 0행을 넣는다 — 회원이 동시에 탈퇴하면 행이 없는데도 "구독됨" 을
	 * 반환하게 된다. ODKU 는 중복만 삼키고 나머지는 그대로 올린다.
	 */
	@Test
	@DisplayName("FK 위반은 삼키지 않는다 — 없는 회원으로 넣으면 예외가 올라온다")
	void upsertDoesNotSwallowForeignKeyViolation() {
		long deletedMemberId = memberId + 1_000_000L;

		assertThatThrownBy(() -> repository.insertIfAbsent(deletedMemberId, playerId, LocalDateTime.now()))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(countRows()).isZero();
	}

	/**
	 * 같은 키를 노리는 세션이 셋 이상 겹치면 upsert 여도 락 순환(1213)이 난다. 선행 세션이 롤백하는
	 * 순간 대기하던 둘이 동시에 깨어나 서로의 락을 기다리기 때문이다. 중복을 어떻게 처리하느냐와
	 * 무관한, InnoDB 가 유니크 인덱스에 gap 락을 잡는 방식의 문제다.
	 *
	 * <p>그래서 {@code MobilePlayerSubscriptionService} 는 예방이 아니라 재시도로 받는다.
	 * 이 테스트는 그 전제(= upsert 로도 1213 이 난다)가 여전히 참인지 지킨다. 락 순환은 타이밍에
	 * 달려 있어 매번 나지는 않으므로, 났을 때 CannotAcquireLockException 이라는 것과
	 * 재시도하면 결국 1행으로 수렴한다는 것만 단정한다.</p>
	 */
	@Test
	@DisplayName("세 세션이 같은 키로 겹치면 upsert 여도 1213 이 날 수 있다 — 재시도하면 1행으로 수렴한다")
	void concurrentUpsertMayDeadlockButRetryConverges() throws Exception {
		int waiters = 2;
		CountDownLatch holderReady = new CountDownLatch(1);
		CountDownLatch waitersIssued = new CountDownLatch(waiters);
		List<Throwable> failures = new ArrayList<>();
		List<Thread> threads = new ArrayList<>();

		// 선행 세션: 같은 키를 먼저 넣고 커밋하지 않은 채 붙잡는다.
		Connection holder = dataSource.getConnection();
		holder.setAutoCommit(false);
		try (PreparedStatement statement = holder.prepareStatement(INSERT_SQL)) {
			statement.setLong(1, memberId);
			statement.setLong(2, playerId);
			statement.setObject(3, LocalDateTime.now());
			statement.executeUpdate();
		}
		holderReady.countDown();

		for (int i = 0; i < waiters; i++) {
			Thread thread = new Thread(() -> {
				try {
					holderReady.await();
					waitersIssued.countDown();
					// 대기자들은 선행 세션이 롤백할 때까지 막혀 있다가 동시에 깨어난다.
					retryOnceOnDeadlock();
				}
				catch (Throwable t) {
					synchronized (failures) {
						failures.add(t);
					}
				}
			});
			threads.add(thread);
			thread.start();
		}

		waitersIssued.await(5, TimeUnit.SECONDS);
		Thread.sleep(500);
		holder.rollback();
		holder.close();

		for (Thread thread : threads) {
			thread.join(TimeUnit.SECONDS.toMillis(20));
		}

		// 재시도가 붙어 있으면 락 순환에 걸렸더라도 끝까지 실패로 남는 요청은 없어야 한다.
		assertThat(failures).isEmpty();
		assertThat(countRows()).isEqualTo(1);
	}

	/** 서비스의 재시도와 같은 규칙 — deadlock 은 예방이 아니라 한 번 다시 시도해서 받는다. */
	private void retryOnceOnDeadlock() {
		try {
			repository.insertIfAbsent(memberId, playerId, LocalDateTime.now());
		}
		catch (CannotAcquireLockException e) {
			repository.insertIfAbsent(memberId, playerId, LocalDateTime.now());
		}
	}

	private int countRows() {
		Integer rows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM member_favorite_player WHERE member_id = ? AND player_id = ?",
				Integer.class, memberId, playerId);
		return rows == null ? 0 : rows;
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
