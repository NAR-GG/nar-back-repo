package com.toy.nar.domain.youtube.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "deadlock.local.enabled", matches = "true")
class VideoDeadlockObservationLocalMySqlIntegrationTest {

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getProperty(
				"deadlock.local.jdbc-url",
				"jdbc:mysql://127.0.0.1:3308/nar_deadlock_test?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"));
		registry.add("spring.datasource.username", () -> System.getProperty("deadlock.local.username", "root"));
		registry.add("spring.datasource.password", () -> System.getProperty("deadlock.local.password", "root_password"));
		registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.flyway.enabled", () -> "false");
	}

	@Autowired
	private ChannelRepository channelRepository;

	@Autowired
	private VideoRepository videoRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private DataSource dataSource;

	private JdbcTemplate jdbcTemplate;
	private Long firstVideoId;
	private Long secondVideoId;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(dataSource);
		videoRepository.deleteAll();
		channelRepository.deleteAll();

		Channel channel = channelRepository.save(Channel.builder()
				.youtubeChannelId("UC_deadlock_observation")
				.channelName("Deadlock Observation Channel")
				.uploadPlaylistId("UPLOADS")
				.channelType(ChannelType.PRO_TEAMS)
				.build());

		Video firstVideo = videoRepository.save(Video.builder()
				.channel(channel)
				.youtubeVideoId("video-first")
				.title("First")
				.thumbnailUrl("http://thumb/first")
				.videoUrl("http://video/first")
				.publishedAt(LocalDateTime.of(2026, 3, 22, 1, 0))
				.viewCount(1L)
				.likeCount(1L)
				.commentCount(1L)
				.build());

		Video secondVideo = videoRepository.save(Video.builder()
				.channel(channel)
				.youtubeVideoId("video-second")
				.title("Second")
				.thumbnailUrl("http://thumb/second")
				.videoUrl("http://video/second")
				.publishedAt(LocalDateTime.of(2026, 3, 22, 2, 0))
				.viewCount(1L)
				.likeCount(1L)
				.commentCount(1L)
				.build());

		videoRepository.flush();
		firstVideoId = firstVideo.getId();
		secondVideoId = secondVideo.getId();
	}

	@AfterEach
	void tearDown() {
		videoRepository.deleteAll();
		channelRepository.deleteAll();
	}

	@Test
	@DisplayName("역순 update deadlock 상황에서 performance_schema와 InnoDB status를 관찰할 수 있다")
	void observeDeadlockWithPerformanceSchema() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch firstLockAcquired = new CountDownLatch(2);
		CountDownLatch allowTx1SecondUpdate = new CountDownLatch(1);
		CountDownLatch allowTx2SecondUpdate = new CountDownLatch(1);

		try {
			Future<TxOutcome> tx1 = executor.submit(() -> runInNewTransaction("tx1", () -> {
				updateVideoAndFlush(firstVideoId, 101L);
				firstLockAcquired.countDown();
				firstLockAcquired.await(5, TimeUnit.SECONDS);
				allowTx1SecondUpdate.await(5, TimeUnit.SECONDS);
				updateVideoAndFlush(secondVideoId, 102L);
			}));

			Future<TxOutcome> tx2 = executor.submit(() -> runInNewTransaction("tx2", () -> {
				updateVideoAndFlush(secondVideoId, 201L);
				firstLockAcquired.countDown();
				firstLockAcquired.await(5, TimeUnit.SECONDS);
				allowTx2SecondUpdate.await(5, TimeUnit.SECONDS);
				updateVideoAndFlush(firstVideoId, 202L);
			}));

			assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS), "두 트랜잭션이 첫 번째 row lock을 잡아야 합니다.");

			List<Map<String, Object>> heldLocks = queryCurrentLocks();
			System.out.println("=== CURRENT DATA_LOCKS ===");
			heldLocks.forEach(System.out::println);

			allowTx1SecondUpdate.countDown();
			Thread.sleep(300);

			List<Map<String, Object>> waits = queryCurrentLockWaits();
			System.out.println("=== CURRENT DATA_LOCK_WAITS ===");
			waits.forEach(System.out::println);

			allowTx2SecondUpdate.countDown();

			TxOutcome tx1Outcome = tx1.get(10, TimeUnit.SECONDS);
			TxOutcome tx2Outcome = tx2.get(10, TimeUnit.SECONDS);

			String innodbStatus = queryLatestDeadlockStatus();
			System.out.println("=== LATEST INNODB STATUS ===");
			System.out.println(innodbStatus);
			System.out.println("=== CONNECTION MAP ===");
			System.out.println(tx1Outcome.label() + " -> connectionId=" + tx1Outcome.connectionId());
			System.out.println(tx2Outcome.label() + " -> connectionId=" + tx2Outcome.connectionId());

			assertTrue(isDeadlock(tx1Outcome.error()) || isDeadlock(tx2Outcome.error()),
					() -> "Expected a deadlock but got tx1=" + tx1Outcome.error() + ", tx2=" + tx2Outcome.error());
			assertNotNull(innodbStatus);
			assertTrue(innodbStatus.contains("LATEST DETECTED DEADLOCK"),
					"InnoDB status should include latest detected deadlock information.");
		} finally {
			executor.shutdownNow();
		}
	}

	private TxOutcome runInNewTransaction(String label, ThrowingRunnable action) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

		final Long[] connectionIdHolder = new Long[1];

		try {
			template.executeWithoutResult(status -> {
				try {
					connectionIdHolder[0] = jdbcTemplate.queryForObject("select connection_id()", Long.class);
					action.run();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			});
			return new TxOutcome(label, connectionIdHolder[0], null);
		} catch (Exception e) {
			return new TxOutcome(label, connectionIdHolder[0], e);
		}
	}

	private void updateVideoAndFlush(Long videoId, Long baseValue) {
		Video video = videoRepository.findById(videoId).orElseThrow();
		video.updateStatistics(baseValue, baseValue, baseValue);
		videoRepository.flush();
	}

	private List<Map<String, Object>> queryCurrentLocks() {
		return jdbcTemplate.queryForList("""
				SELECT
				    t.PROCESSLIST_ID AS processlist_id,
				    dl.ENGINE_TRANSACTION_ID,
				    dl.OBJECT_SCHEMA,
				    dl.OBJECT_NAME,
				    dl.INDEX_NAME,
				    dl.LOCK_TYPE,
				    dl.LOCK_MODE,
				    dl.LOCK_STATUS,
				    dl.LOCK_DATA
				FROM performance_schema.data_locks dl
				JOIN performance_schema.threads t
				  ON dl.THREAD_ID = t.THREAD_ID
				WHERE dl.OBJECT_NAME = 'video'
				ORDER BY processlist_id, dl.LOCK_DATA
				""");
	}

	private List<Map<String, Object>> queryCurrentLockWaits() {
		return jdbcTemplate.queryForList("""
				SELECT
				    rt.PROCESSLIST_ID AS waiting_conn,
				    bt.PROCESSLIST_ID AS blocking_conn,
				    rl.OBJECT_SCHEMA,
				    rl.OBJECT_NAME,
				    rl.INDEX_NAME,
				    rl.LOCK_MODE AS waiting_lock_mode,
				    bl.LOCK_MODE AS blocking_lock_mode,
				    rl.LOCK_DATA
				FROM performance_schema.data_lock_waits w
				JOIN performance_schema.data_locks rl
				  ON w.REQUESTING_ENGINE_LOCK_ID = rl.ENGINE_LOCK_ID
				 AND w.ENGINE = rl.ENGINE
				JOIN performance_schema.data_locks bl
				  ON w.BLOCKING_ENGINE_LOCK_ID = bl.ENGINE_LOCK_ID
				 AND w.ENGINE = bl.ENGINE
				JOIN performance_schema.threads rt
				  ON rl.THREAD_ID = rt.THREAD_ID
				JOIN performance_schema.threads bt
				  ON bl.THREAD_ID = bt.THREAD_ID
				WHERE rl.OBJECT_NAME = 'video'
				ORDER BY waiting_conn, blocking_conn
				""");
	}

	private String queryLatestDeadlockStatus() {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW ENGINE INNODB STATUS");
		if (rows.isEmpty()) {
			return null;
		}
		Object status = rows.get(0).get("Status");
		return status == null ? null : status.toString();
	}

	private boolean isDeadlock(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof CannotAcquireLockException
					|| current instanceof ObjectOptimisticLockingFailureException
					|| current instanceof TransactionSystemException) {
				return true;
			}
			String message = current.getMessage();
			if (message != null && message.contains("Deadlock found when trying to get lock")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private record TxOutcome(String label, Long connectionId, Throwable error) {
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
