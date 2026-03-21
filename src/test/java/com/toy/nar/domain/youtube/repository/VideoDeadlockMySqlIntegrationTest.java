package com.toy.nar.domain.youtube.repository;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Video;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "deadlock.test.enabled", matches = "true")
class VideoDeadlockMySqlIntegrationTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
			.withDatabaseName("nar_test")
			.withUsername("test")
			.withPassword("test");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.flyway.enabled", () -> "false");
	}

	@Autowired
	private ChannelRepository channelRepository;

	@Autowired
	private VideoRepository videoRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private Long firstVideoId;
	private Long secondVideoId;

	@BeforeEach
	void setUp() {
		videoRepository.deleteAll();
		channelRepository.deleteAll();

		Channel channel = channelRepository.save(Channel.builder()
				.youtubeChannelId("UC_deadlock_test")
				.channelName("Deadlock Test Channel")
				.uploadPlaylistId("UPLOADS")
				.channelType(ChannelType.PRO_TEAMS)
				.build());

		Video firstVideo = videoRepository.save(Video.builder()
				.channel(channel)
				.youtubeVideoId("video-first")
				.title("First")
				.thumbnailUrl("http://thumb/first")
				.videoUrl("http://video/first")
				.publishedAt(LocalDateTime.of(2026, 3, 20, 1, 0))
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
				.publishedAt(LocalDateTime.of(2026, 3, 20, 2, 0))
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
	@DisplayName("역순으로 같은 row를 업데이트하면 MySQL에서 deadlock을 재현할 수 있다")
	void reproducesDeadlockWhenUpdateOrderIsReversed() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch firstLockAcquired = new CountDownLatch(2);

		try {
			Future<Throwable> tx1 = executor.submit(reverseOrderWorker(firstVideoId, secondVideoId, firstLockAcquired, 11L));
			Future<Throwable> tx2 = executor.submit(reverseOrderWorker(secondVideoId, firstVideoId, firstLockAcquired, 21L));

			Throwable firstResult = tx1.get(10, TimeUnit.SECONDS);
			Throwable secondResult = tx2.get(10, TimeUnit.SECONDS);

			assertTrue(isDeadlock(firstResult) || isDeadlock(secondResult),
					() -> "Expected a deadlock but got tx1=" + firstResult + ", tx2=" + secondResult);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("같은 순서로 업데이트하면 대기 후 정상 완료된다")
	void sameOrderUpdatesWaitButDoNotDeadlock() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch firstLockHeld = new CountDownLatch(1);

		try {
			Future<Throwable> tx1 = executor.submit(() -> runInNewTransaction(() -> {
				updateVideoAndFlush(firstVideoId, 101L);
				firstLockHeld.countDown();
				sleep(500);
				updateVideoAndFlush(secondVideoId, 102L);
			}));

			Future<Throwable> tx2 = executor.submit(() -> {
				firstLockHeld.await(5, TimeUnit.SECONDS);
				return runInNewTransaction(() -> {
					updateVideoAndFlush(firstVideoId, 201L);
					updateVideoAndFlush(secondVideoId, 202L);
				});
			});

			assertNull(tx1.get(10, TimeUnit.SECONDS));
			assertNull(tx2.get(10, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}
	}

	private Callable<Throwable> reverseOrderWorker(
			Long firstId,
			Long secondId,
			CountDownLatch firstLockAcquired,
			Long seedValue) {
		return () -> runInNewTransaction(() -> {
			updateVideoAndFlush(firstId, seedValue);
			firstLockAcquired.countDown();
			firstLockAcquired.await(5, TimeUnit.SECONDS);
			updateVideoAndFlush(secondId, seedValue + 1);
		});
	}

	private Throwable runInNewTransaction(ThrowingRunnable action) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

		try {
			template.executeWithoutResult(status -> {
				try {
					action.run();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			});
			return null;
		} catch (Exception e) {
			return e;
		}
	}

	private void updateVideoAndFlush(Long videoId, Long baseValue) {
		Video video = videoRepository.findById(videoId).orElseThrow();
		video.updateStatistics(baseValue, baseValue, baseValue);
		videoRepository.flush();
	}

	private void sleep(long millis) throws InterruptedException {
		Thread.sleep(millis);
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

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
