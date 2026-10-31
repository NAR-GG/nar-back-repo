package com.toy.nar.app.member.service;

import com.toy.nar.app.monitor.SchedulerAlertService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 탈퇴 기록 SQL 을 실제 MySQL 에서 검증한다. {@code INSERT ... SELECT ... ON DUPLICATE KEY}, {@code FOR UPDATE} 동시성, 와 제재 서브쿼리는 H2 로 재현되지 않는다.
 * 로컬 OrbStack 에서는 api.version 주입 없이 돌리면 조용히 skipped 된다.
 */
@Testcontainers(disabledWithoutDocker = true)
class MemberWithdrawalMySqlIntegrationTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
			.withDatabaseName("nar_member_withdrawal_test")
			.withUsername("test")
			.withPassword("test")
			.withInitScript("db/pre_v31_schema.sql");

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);

	private static JdbcTemplate jdbc;
	private static MemberWithdrawalService service;
	private static TransactionTemplate tx;

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion(MigrationVersion.fromVersion("30"))
				.load()
				.migrate();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		service = new MemberWithdrawalService(jdbc, mock(SchedulerAlertService.class));
	}

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM member_withdrawal");
		jdbc.update("DELETE FROM member_community_restriction");
		jdbc.update("DELETE FROM member_social");
		jdbc.update("DELETE FROM member");
		jdbc.update("INSERT INTO member (id, name, tag, role, created_at, onboarded_at) "
				+ "VALUES (7, 'tester', 'T1', 'USER', '2026-10-01 09:00:00', '2026-10-01 09:05:00')");
		jdbc.update("INSERT INTO member_social (member_id, provider, provider_id) VALUES (7, 'KAKAO', '123')");
		jdbc.update("INSERT INTO member_social (member_id, provider, provider_id) VALUES (7, 'APPLE', 'abc')");
	}

	@Test
	void recordsJoinAndWithdrawTimesWithHashedSocials() {
		service.record(7L, NOW);

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM member_withdrawal WHERE member_id = 7");
		assertThat(row.get("providers")).isEqualTo("KAKAO,APPLE");
		assertThat(row.get("social_id_hashes")).isEqualTo(
				MemberWithdrawalService.sha256Hex("KAKAO:123") + "," + MemberWithdrawalService.sha256Hex("APPLE:abc"));
		assertThat(row.get("joined_at")).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
		assertThat(row.get("onboarded_at")).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 5));
		assertThat(row.get("withdrawn_at")).isEqualTo(NOW);
		assertThat(row.get("restricted_until")).isNull();
		assertThat(row.get("reason")).isNull();
	}

	@Test
	void repeatedOrMissingMemberIsNoOp() {
		service.record(7L, NOW);
		service.record(7L, NOW.plusSeconds(1));
		service.record(999L, NOW);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_withdrawal", Integer.class)).isEqualTo(1);
	}

	/**
	 * 연타 탈퇴: A 가 기록을 남긴 뒤 회원을 지우기 전에 B 가 기록에 들어온다.
	 * 회원 행 락 없이는 B 가 member S 락 + UNIQUE 키 대기, A 가 DELETE member 에서 B 를 기다려 1213 데드락이 났다.
	 */
	@Test
	void concurrentDoubleTapDoesNotDeadlock() throws Exception {
		CountDownLatch aRecorded = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> a = pool.submit(() -> tx.executeWithoutResult(s -> {
				service.record(7L, NOW);
				aRecorded.countDown();
				sleep(500);
				withdrawDeletes();
			}));
			Future<?> b = pool.submit(() -> {
				await(aRecorded);
				tx.executeWithoutResult(s -> {
					service.record(7L, NOW.plusSeconds(1));
					withdrawDeletes();
				});
			});
			a.get(10, TimeUnit.SECONDS);
			b.get(10, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_withdrawal", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT withdrawn_at FROM member_withdrawal", LocalDateTime.class)).isEqualTo(NOW);
	}

	private static void withdrawDeletes() {
		jdbc.update("DELETE FROM member_social WHERE member_id = 7");
		jdbc.update("DELETE FROM member WHERE id = 7");
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Test
	void keepsLatestActiveRestrictionAndPermanentWins() {
		jdbc.update("INSERT INTO member_community_restriction (member_id, reason, until) VALUES (7, 'old', '2026-10-15')");
		jdbc.update("INSERT INTO member_community_restriction (member_id, reason, until) VALUES (7, 'now', '2026-11-20')");
		service.record(7L, NOW);
		assertThat(restrictedUntil()).isEqualTo(LocalDateTime.of(2026, 11, 20, 0, 0));

		jdbc.update("DELETE FROM member_withdrawal");
		jdbc.update("INSERT INTO member_community_restriction (member_id, reason, until) VALUES (7, 'perm', NULL)");
		service.record(7L, NOW);
		assertThat(restrictedUntil()).isEqualTo(MemberWithdrawalService.PERMANENT);
	}

	@Test
	void clearsHashAfterRetentionUnlessStillRestricted() {
		service.record(7L, NOW);
		jdbc.update("INSERT INTO member_withdrawal (member_id, providers, social_id_hashes, joined_at, withdrawn_at, restricted_until) "
				+ "VALUES (8, 'KAKAO', 'h', '2026-09-01', '2026-09-20', '2026-12-31')");

		int cleared = service.clearExpiredHashes(NOW.plusDays(31));

		assertThat(cleared).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT social_id_hashes FROM member_withdrawal WHERE member_id = 7", String.class)).isNull();
		assertThat(jdbc.queryForObject("SELECT social_id_hashes FROM member_withdrawal WHERE member_id = 8", String.class)).isEqualTo("h");
	}

	private LocalDateTime restrictedUntil() {
		return jdbc.queryForObject("SELECT restricted_until FROM member_withdrawal WHERE member_id = 7", LocalDateTime.class);
	}
}
