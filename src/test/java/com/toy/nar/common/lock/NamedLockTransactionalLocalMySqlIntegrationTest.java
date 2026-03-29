package com.toy.nar.common.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@JdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ContextConfiguration(classes = NamedLockTransactionalLocalMySqlIntegrationTest.TestConfig.class)
@ImportAutoConfiguration({
		DataSourceAutoConfiguration.class,
		JdbcTemplateAutoConfiguration.class,
		TransactionAutoConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "deadlock.local.enabled", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NamedLockTransactionalLocalMySqlIntegrationTest {

	private static final String JDBC_URL = System.getProperty(
			"deadlock.local.jdbc-url",
			"jdbc:mysql://127.0.0.1:3308/nar_named_lock_test?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul");
	private static final String USERNAME = System.getProperty("deadlock.local.username", "root");
	private static final String PASSWORD = System.getProperty("deadlock.local.password", "root_password");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> JDBC_URL);
		registry.add("spring.datasource.username", () -> USERNAME);
		registry.add("spring.datasource.password", () -> PASSWORD);
		registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
		registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
		registry.add("spring.datasource.hikari.pool-name", () -> "named-lock-test-pool");
	}

	@Autowired
	private NamedLockTransactionalProbe probe;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		probe.clearEvents();
		jdbcTemplate.execute("""
				create table if not exists named_lock_business_marker (
				    id bigint auto_increment primary key,
				    lock_name varchar(255) not null,
				    business_connection_id bigint not null,
				    note varchar(255) not null
				)
				""");
		jdbcTemplate.update("delete from named_lock_business_marker");
	}

	@AfterEach
	void tearDown() {
		probe.clearEvents();
		jdbcTemplate.update("delete from named_lock_business_marker");
	}

	@Test
	@DisplayName("@Transactional 밖에서는 GET_LOCK과 다음 쿼리가 같은 커넥션이라는 보장이 없다")
	void nonTransactionalLockAndBusinessMayUseDifferentConnections() throws Exception {
		String lockName = newLockName("non-tx");

		NonTransactionalTrace trace = probe.acquireLockWithoutTransactionAndForceDifferentBusinessConnection(lockName);

		assertEquals(1, trace.lockResult());
		assertEquals(trace.lockConnectionId(), trace.heldConnectionId());
		assertNotEquals(trace.lockConnectionId(), trace.businessConnectionId());
		assertEquals(0, trace.releaseResultFromBusinessConnection());

		System.out.println("=== NON TRANSACTIONAL TRACE ===");
		System.out.println(trace);

		assertNull(queryUsedLock(lockName));
	}

	@Test
	@DisplayName("@Transactional 안에서는 GET_LOCK과 비즈니스 쿼리가 같은 커넥션을 사용한다")
	void transactionalLockAndBusinessUseSameConnection() throws Exception {
		String lockName = newLockName("tx-without-release");

		LockTrace trace = probe.acquireLockInsideTransactionWithoutRelease(lockName);

		assertEquals(1, trace.lockResult());
		assertEquals(trace.lockConnectionId(), trace.businessConnectionId());

		Long usedLockConnectionId = queryUsedLock(lockName);
		assertEquals(trace.lockConnectionId(), usedLockConnectionId);

		System.out.println("=== SAME CONNECTION TRACE ===");
		System.out.println(trace);
		System.out.println("observer is_used_lock=" + usedLockConnectionId);
	}

	@Test
	@DisplayName("트랜잭션 종료 후 커넥션은 풀로 반납되지만 named lock은 그대로 남아 있다")
	void transactionCompletionReturnsConnectionToPoolButDoesNotReleaseNamedLock() throws Exception {
		String lockName = newLockName("tx-without-release");

		LockTrace trace = probe.acquireLockInsideTransactionWithoutRelease(lockName);

		assertEquals(1, trace.lockResult());
		Long usedLockConnectionId = queryUsedLock(lockName);
		assertEquals(trace.lockConnectionId(), usedLockConnectionId);

		try (Connection pooledConnection = dataSource.getConnection();
				Statement statement = pooledConnection.createStatement()) {
			Long borrowedConnectionId = singleLong(statement.executeQuery("select connection_id()"));
			Integer releaseResult = singleInt(statement.executeQuery(
					"select release_lock('" + lockName + "')"));

			System.out.println("=== POOL RETURN TRACE ===");
			System.out.println("transaction connectionId=" + trace.lockConnectionId());
			System.out.println("borrowed from pool connectionId=" + borrowedConnectionId);
			System.out.println("release_lock result=" + releaseResult);

			assertEquals(trace.lockConnectionId(), borrowedConnectionId);
			assertEquals(1, releaseResult);
		}

		assertNull(queryUsedLock(lockName));
	}

	@Test
	@DisplayName("@Transactional 비즈니스가 다른 커넥션이면 외부 실패와 독립적으로 커밋될 수 있다")
	void transactionalBusinessCanCommitIndependentlyFromOuterNamedLockFlow() throws Exception {
		String lockName = newLockName("outer-failure");

		RuntimeException thrown = null;
		try {
			probe.acquireLockOutsideTransactionRunTransactionalBusinessThenFail(lockName);
		} catch (RuntimeException e) {
			thrown = e;
		}

		assertNotNull(thrown);
		assertTrue(thrown.getMessage().contains("outer-fail"));
		assertNull(queryUsedLock(lockName));

		List<String> events = probe.events();
		Long outerConnectionId = valueAfterPrefix(events, "outerConnection:");
		Long businessConnectionId = valueAfterPrefix(events, "businessConnection:");
		Integer outerReleaseResult = valueAfterPrefixAsInt(events, "outerRelease:");

		assertNotNull(outerConnectionId);
		assertNotNull(businessConnectionId);
		assertNotEquals(outerConnectionId, businessConnectionId);
		assertEquals(1, outerReleaseResult);

		Integer committedRows = jdbcTemplate.queryForObject(
				"select count(*) from named_lock_business_marker where lock_name = ?",
				Integer.class,
				lockName);
		assertEquals(1, committedRows);

		Long storedBusinessConnectionId = jdbcTemplate.queryForObject(
				"select business_connection_id from named_lock_business_marker where lock_name = ?",
				Long.class,
				lockName);
		assertEquals(businessConnectionId, storedBusinessConnectionId);

		System.out.println("=== OUTER LOCK / INNER TX TRACE ===");
		events.forEach(System.out::println);
		System.out.println("committedRows=" + committedRows);
		System.out.println("storedBusinessConnectionId=" + storedBusinessConnectionId);
	}

	@Test
	@DisplayName("finally 에서 releaseLock 하면 rollback 완료 전에 락이 먼저 해제된다")
	void finallyReleaseRunsBeforeTransactionCompletion() throws Exception {
		String lockName = newLockName("tx-with-finally");

		RuntimeException thrown = null;
		try {
			probe.acquireLockAndReleaseInFinallyThenFail(lockName);
		} catch (RuntimeException e) {
			thrown = e;
		}

		assertNotNull(thrown);
		assertTrue(thrown.getMessage().contains("boom"));
		assertNull(queryUsedLock(lockName));

		List<String> events = probe.events();
		System.out.println("=== FINALLY ORDER TRACE ===");
		events.forEach(System.out::println);

		assertFalse(events.isEmpty());
		assertTrue(events.get(0).startsWith("locked:"));
		assertTrue(events.stream().anyMatch(event -> event.startsWith("release:")));

		int releaseIndex = indexOf(events, "release:");
		int afterCompletionIndex = indexOf(events, "afterCompletion:ROLLED_BACK");

		assertTrue(releaseIndex >= 0);
		assertTrue(afterCompletionIndex >= 0);
		assertTrue(releaseIndex < afterCompletionIndex,
				"releaseLock should run before transaction afterCompletion rollback callback");
	}

	private Long queryUsedLock(String lockName) throws SQLException {
		try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
				Statement statement = connection.createStatement()) {
			return singleNullableLong(statement.executeQuery("select is_used_lock('" + lockName + "')"));
		}
	}

	private String newLockName(String prefix) {
		return "named-lock-test:" + prefix + ":" + System.nanoTime();
	}

	private int indexOf(List<String> events, String prefix) {
		for (int i = 0; i < events.size(); i++) {
			if (events.get(i).startsWith(prefix)) {
				return i;
			}
		}
		return -1;
	}

	private Long valueAfterPrefix(List<String> events, String prefix) {
		int index = indexOf(events, prefix);
		if (index < 0) {
			return null;
		}
		return Long.parseLong(events.get(index).substring(prefix.length()));
	}

	private Integer valueAfterPrefixAsInt(List<String> events, String prefix) {
		int index = indexOf(events, prefix);
		if (index < 0) {
			return null;
		}
		return Integer.parseInt(events.get(index).substring(prefix.length()));
	}

	private Long singleLong(ResultSet rs) throws SQLException {
		if (!rs.next()) {
			return null;
		}
		return rs.getLong(1);
	}

	private Long singleNullableLong(ResultSet rs) throws SQLException {
		if (!rs.next()) {
			return null;
		}
		Object value = rs.getObject(1);
		return value == null ? null : ((Number) value).longValue();
	}

	private Integer singleInt(ResultSet rs) throws SQLException {
		if (!rs.next()) {
			return null;
		}
		return rs.getInt(1);
	}

	static final class LockTrace {
		private final Long lockConnectionId;
		private final Integer lockResult;
		private final Long businessConnectionId;

		LockTrace(Long lockConnectionId, Integer lockResult, Long businessConnectionId) {
			this.lockConnectionId = lockConnectionId;
			this.lockResult = lockResult;
			this.businessConnectionId = businessConnectionId;
		}

		Long lockConnectionId() {
			return lockConnectionId;
		}

		Integer lockResult() {
			return lockResult;
		}

		Long businessConnectionId() {
			return businessConnectionId;
		}

		@Override
		public String toString() {
			return "LockTrace{lockConnectionId=" + lockConnectionId
					+ ", lockResult=" + lockResult
					+ ", businessConnectionId=" + businessConnectionId + "}";
		}
	}

	static final class NonTransactionalTrace {
		private final Long lockConnectionId;
		private final Integer lockResult;
		private final Long heldConnectionId;
		private final Long businessConnectionId;
		private final Integer releaseResultFromBusinessConnection;

		NonTransactionalTrace(Long lockConnectionId, Integer lockResult, Long heldConnectionId,
				Long businessConnectionId, Integer releaseResultFromBusinessConnection) {
			this.lockConnectionId = lockConnectionId;
			this.lockResult = lockResult;
			this.heldConnectionId = heldConnectionId;
			this.businessConnectionId = businessConnectionId;
			this.releaseResultFromBusinessConnection = releaseResultFromBusinessConnection;
		}

		Long lockConnectionId() {
			return lockConnectionId;
		}

		Integer lockResult() {
			return lockResult;
		}

		Long heldConnectionId() {
			return heldConnectionId;
		}

		Long businessConnectionId() {
			return businessConnectionId;
		}

		Integer releaseResultFromBusinessConnection() {
			return releaseResultFromBusinessConnection;
		}

		@Override
		public String toString() {
			return "NonTransactionalTrace{lockConnectionId=" + lockConnectionId
					+ ", lockResult=" + lockResult
					+ ", heldConnectionId=" + heldConnectionId
					+ ", businessConnectionId=" + businessConnectionId
					+ ", releaseResultFromBusinessConnection=" + releaseResultFromBusinessConnection + "}";
		}
	}

	@Configuration
	static class TestConfig {

		@Bean
		NamedLockTransactionalProbe namedLockTransactionalProbe(
				JdbcTemplate jdbcTemplate,
				DataSource dataSource,
				NamedLockTransactionalBusinessWorker businessWorker) {
			return new NamedLockTransactionalProbe(jdbcTemplate, dataSource, businessWorker);
		}

		@Bean
		NamedLockTransactionalBusinessWorker namedLockTransactionalBusinessWorker(JdbcTemplate jdbcTemplate) {
			return new NamedLockTransactionalBusinessWorker(jdbcTemplate);
		}
	}

	static class NamedLockTransactionalProbe {

		private final JdbcTemplate jdbcTemplate;
		private final DataSource dataSource;
		private final NamedLockTransactionalBusinessWorker businessWorker;
		private final List<String> events = new ArrayList<>();

		NamedLockTransactionalProbe(
				JdbcTemplate jdbcTemplate,
				DataSource dataSource,
				NamedLockTransactionalBusinessWorker businessWorker) {
			this.jdbcTemplate = jdbcTemplate;
			this.dataSource = dataSource;
			this.businessWorker = businessWorker;
		}

		public NonTransactionalTrace acquireLockWithoutTransactionAndForceDifferentBusinessConnection(String lockName)
				throws SQLException {
			LockTrace trace = jdbcTemplate.query("select connection_id(), get_lock(?, 0)",
					rs -> {
						rs.next();
						return new LockTrace(rs.getLong(1), rs.getInt(2), null);
					},
					lockName);

			try (Connection heldConnection = dataSource.getConnection();
					Statement heldStatement = heldConnection.createStatement()) {
				Long heldConnectionId = readSingleLong(heldStatement.executeQuery("select connection_id()"));
				Long businessConnectionId = jdbcTemplate.queryForObject("select connection_id()", Long.class);
				Integer releaseResultFromBusinessConnection = jdbcTemplate.queryForObject(
						"select release_lock(?)", Integer.class, lockName);
				Integer releaseResultFromHeldConnection = readSingleInt(
						heldStatement.executeQuery("select release_lock('" + lockName + "')"));

				assertEquals(1, releaseResultFromHeldConnection);

				return new NonTransactionalTrace(
						trace.lockConnectionId(),
						trace.lockResult(),
						heldConnectionId,
						businessConnectionId,
						releaseResultFromBusinessConnection);
			}
		}

		public void acquireLockOutsideTransactionRunTransactionalBusinessThenFail(String lockName) throws SQLException {
			try (Connection lockConnection = dataSource.getConnection();
					Statement lockStatement = lockConnection.createStatement()) {
				Long outerConnectionId = readSingleLong(lockStatement.executeQuery("select connection_id()"));
				events.add("outerConnection:" + outerConnectionId);

				Integer lockResult = readSingleInt(lockStatement.executeQuery(
						"select get_lock('" + lockName + "', 0)"));
				events.add("outerGetLock:" + lockResult);

				try {
					Long businessConnectionId = businessWorker.insertMarkerInTransactionalBusiness(lockName);
					events.add("businessConnection:" + businessConnectionId);
					throw new RuntimeException("outer-fail");
				} finally {
					Integer releaseResult = readSingleInt(lockStatement.executeQuery(
							"select release_lock('" + lockName + "')"));
					events.add("outerRelease:" + releaseResult);
				}
			}
		}

		private Long readSingleLong(ResultSet rs) throws SQLException {
			if (!rs.next()) {
				return null;
			}
			return rs.getLong(1);
		}

		private Integer readSingleInt(ResultSet rs) throws SQLException {
			if (!rs.next()) {
				return null;
			}
			return rs.getInt(1);
		}

		@Transactional
		public LockTrace acquireLockInsideTransactionWithoutRelease(String lockName) {
			LockTrace trace = jdbcTemplate.query("select connection_id(), get_lock(?, 0)",
					rs -> {
						rs.next();
						return new LockTrace(rs.getLong(1), rs.getInt(2), null);
					},
					lockName);

			Long businessConnectionId = jdbcTemplate.queryForObject("select connection_id()", Long.class);
			return new LockTrace(trace.lockConnectionId(), trace.lockResult(), businessConnectionId);
		}

		@Transactional
		public void acquireLockAndReleaseInFinallyThenFail(String lockName) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					String completion = switch (status) {
						case STATUS_COMMITTED -> "COMMITTED";
						case STATUS_ROLLED_BACK -> "ROLLED_BACK";
						default -> "UNKNOWN";
					};
					events.add("afterCompletion:" + completion);
				}
			});

			Long lockConnectionId = jdbcTemplate.query("select connection_id(), get_lock(?, 0)",
					rs -> {
						rs.next();
						return rs.getLong(1);
					},
					lockName);
			events.add("locked:" + lockConnectionId);

			try {
				Long businessConnectionId = jdbcTemplate.queryForObject("select connection_id()", Long.class);
				events.add("business:" + businessConnectionId);
				throw new RuntimeException("boom");
			} finally {
				Integer releaseResult = jdbcTemplate.query("select connection_id(), release_lock(?)",
						rs -> {
							rs.next();
							events.add("releaseConnection:" + rs.getLong(1));
							return rs.getInt(2);
						},
						lockName);
				events.add("release:" + releaseResult);
			}
		}

		List<String> events() {
			return List.copyOf(events);
		}

		void clearEvents() {
			events.clear();
		}
	}

	static class NamedLockTransactionalBusinessWorker {

		private final JdbcTemplate jdbcTemplate;

		NamedLockTransactionalBusinessWorker(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
		}

		@Transactional
		public Long insertMarkerInTransactionalBusiness(String lockName) {
			Long businessConnectionId = jdbcTemplate.queryForObject("select connection_id()", Long.class);
			jdbcTemplate.update(
					"insert into named_lock_business_marker(lock_name, business_connection_id, note) values (?, ?, ?)",
					lockName,
					businessConnectionId,
					"committed");
			return businessConnectionId;
		}
	}
}
