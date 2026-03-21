package com.toy.nar.app.youtube;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.toy.nar.app.youtube.dto.YoutubeCommentResponse;
import com.toy.nar.app.youtube.dto.YoutubePlaylistResponse;
import com.toy.nar.app.youtube.dto.YoutubeSearchResponse.Thumbnail;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse.VideoItem;
import com.toy.nar.app.youtube.dto.YoutubeVideoResponse.VideoStatistics;
import com.toy.nar.common.util.YoutubeProperties;
import com.toy.nar.domain.youtube.Channel;
import com.toy.nar.domain.youtube.ChannelType;
import com.toy.nar.domain.youtube.Comment;
import com.toy.nar.domain.youtube.Video;
import com.toy.nar.domain.youtube.repository.ChannelRepository;
import com.toy.nar.domain.youtube.repository.CommentRepository;
import com.toy.nar.domain.youtube.repository.VideoRepository;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "deadlock.test.enabled", matches = "true")
@Import({
		YoutubeSyncService.class,
		YoutubeChannelSyncTxService.class,
		YoutubeThreeAmConcurrencyMySqlIntegrationTest.MockBeans.class
})
class YoutubeThreeAmConcurrencyMySqlIntegrationTest {

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
	private YoutubeSyncService youtubeSyncService;

	@Autowired
	private YoutubeService youtubeService;

	@Autowired
	private ChannelRepository channelRepository;

	@Autowired
	private VideoRepository videoRepository;

	@Autowired
	private CommentRepository commentRepository;

	private LocalDateTime baseTime;

	@BeforeEach
	void setUp() throws Exception {
		commentRepository.deleteAll();
		videoRepository.deleteAll();
		channelRepository.deleteAll();
		reset(youtubeService);

		baseTime = LocalDateTime.now().minusHours(1);
		Channel channel = channelRepository.save(Channel.builder()
				.youtubeChannelId("UC_3am_test")
				.channelName("3AM Test Channel")
				.uploadPlaylistId("UPLOADS")
				.channelType(ChannelType.PRO_TEAMS)
				.build());

		saveVideo(channel, "video-a", "Video A", baseTime.minusMinutes(20));
		saveVideo(channel, "video-b", "Video B", baseTime.minusMinutes(10));
		saveVideo(channel, "video-c", "Video C", baseTime.minusMinutes(5));
		videoRepository.flush();

		stubYoutubeApis();
	}

	@AfterEach
	void tearDown() {
		commentRepository.deleteAll();
		videoRepository.deleteAll();
		channelRepository.deleteAll();
	}

	@Test
	@DisplayName("3시 시나리오처럼 주간/24시간/3시간/댓글 동기화를 동시에 실행해도 deadlock 없이 끝난다")
	void threeAmJobsCompleteWithoutDeadlock() throws Exception {
		for (int iteration = 0; iteration < 5; iteration++) {
			ExecutorService executor = Executors.newFixedThreadPool(4);
			CountDownLatch startGate = new CountDownLatch(1);

			try {
				Future<Throwable> weekly = executor.submit(runAfterStart(startGate, youtubeSyncService::syncLastWeekVideos));
				Future<Throwable> stats24h = executor.submit(runAfterStart(startGate,
						() -> youtubeSyncService.syncVideoStatisticsByPublishedAfter(LocalDateTime.now().minusDays(1))));
				Future<Throwable> stats3h = executor.submit(runAfterStart(startGate,
						() -> youtubeSyncService.syncVideoStatisticsByPublishedAfter(LocalDateTime.now().minusHours(3))));
				Future<Throwable> comments = executor.submit(runAfterStart(startGate, youtubeSyncService::syncRecentComments));

				startGate.countDown();

				Throwable weeklyResult = weekly.get(20, TimeUnit.SECONDS);
				Throwable stats24hResult = stats24h.get(20, TimeUnit.SECONDS);
				Throwable stats3hResult = stats3h.get(20, TimeUnit.SECONDS);
				Throwable commentsResult = comments.get(20, TimeUnit.SECONDS);

				assertNoDeadlock("weekly", weeklyResult);
				assertNoDeadlock("24h", stats24hResult);
				assertNoDeadlock("3h", stats3hResult);
				assertNoDeadlock("comments", commentsResult);
			} finally {
				executor.shutdownNow();
			}
		}
	}

	private Callable<Throwable> runAfterStart(CountDownLatch startGate, ThrowingRunnable action) {
		return () -> {
			startGate.await(5, TimeUnit.SECONDS);
			try {
				action.run();
				return null;
			} catch (Throwable t) {
				return t;
			}
		};
	}

	private void stubYoutubeApis() throws Exception {
		CyclicBarrier videoDetailsBarrier = new CyclicBarrier(3);
		AtomicInteger detailCallCount = new AtomicInteger();

		when(youtubeService.getPlaylistItems(eq("UPLOADS"), any())).thenReturn(playlistResponse());
		when(youtubeService.getVideoDetails(anyList())).thenAnswer(invocation -> {
			if (detailCallCount.incrementAndGet() <= 3) {
				videoDetailsBarrier.await(3, TimeUnit.SECONDS);
			}
			Thread.sleep(50);
			@SuppressWarnings("unchecked")
			List<String> ids = invocation.getArgument(0, List.class);
			return videoResponse(ids);
		});
		when(youtubeService.getVideoComments(any())).thenAnswer(invocation -> {
			Thread.sleep(30);
			return emptyCommentResponse();
		});
		when(youtubeService.extractBestThumbnailUrl(any())).thenReturn("http://thumb.url");
	}

	private YoutubePlaylistResponse playlistResponse() {
		List<YoutubePlaylistResponse.PlaylistItem> items = List.of(
				playlistItem("video-a", baseTime.minusMinutes(20)),
				playlistItem("video-b", baseTime.minusMinutes(10)),
				playlistItem("video-c", baseTime.minusMinutes(5)));
		return new YoutubePlaylistResponse(null, null, null, items);
	}

	private YoutubePlaylistResponse.PlaylistItem playlistItem(String videoId, LocalDateTime publishedAt) {
		return new YoutubePlaylistResponse.PlaylistItem(
				"playlist-" + videoId,
				new YoutubePlaylistResponse.Snippet(
						publishedAt.atOffset(OffsetDateTime.now().getOffset()).toString(),
						"UC_3am_test",
						"Title " + videoId,
						"Description",
						Map.of("default", new YoutubePlaylistResponse.Thumbnail("http://thumb.url", 120, 90)),
						new YoutubePlaylistResponse.ResourceId("youtube#video", videoId)),
				new YoutubePlaylistResponse.ContentDetails(videoId,
						publishedAt.atOffset(OffsetDateTime.now().getOffset()).toString()));
	}

	private YoutubeVideoResponse videoResponse(List<String> ids) {
		List<VideoItem> items = ids.stream()
				.map(id -> new VideoItem(
						id,
						new com.toy.nar.app.youtube.dto.YoutubeSearchResponse.SearchSnippet(
								publishedAtFor(id).toString(),
								"UC_3am_test",
								"Title " + id,
								"Description",
								Map.of("default", new Thumbnail("http://thumb.url", 120, 90))),
						new VideoStatistics("100", "10", "5")))
				.toList();
		return new YoutubeVideoResponse(items);
	}

	private YoutubeCommentResponse emptyCommentResponse() {
		return new YoutubeCommentResponse(List.of());
	}

	private OffsetDateTime publishedAtFor(String videoId) {
		LocalDateTime publishedAt = switch (videoId) {
			case "video-a" -> baseTime.minusMinutes(20);
			case "video-b" -> baseTime.minusMinutes(10);
			case "video-c" -> baseTime.minusMinutes(5);
			default -> baseTime;
		};
		return publishedAt.atOffset(OffsetDateTime.now().getOffset());
	}

	private void saveVideo(Channel channel, String youtubeVideoId, String title, LocalDateTime publishedAt) {
		videoRepository.save(Video.builder()
				.channel(channel)
				.youtubeVideoId(youtubeVideoId)
				.title(title)
				.thumbnailUrl("http://thumb.url")
				.videoUrl("http://video/" + youtubeVideoId)
				.publishedAt(publishedAt)
				.viewCount(1L)
				.likeCount(1L)
				.commentCount(1L)
				.build());
	}

	private void assertNoDeadlock(String jobName, Throwable throwable) {
		assertTrue(!isDeadlock(throwable),
				() -> "Expected no deadlock for " + jobName + " but got " + throwable);
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

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EntityScan(basePackageClasses = { Channel.class, Video.class, Comment.class })
	@EnableJpaRepositories(basePackageClasses = {
			ChannelRepository.class,
			VideoRepository.class,
			CommentRepository.class
	})
	static class TestJpaConfiguration {
	}

	static class MockBeans {
		@Bean
		YoutubeService youtubeService() {
			return org.mockito.Mockito.mock(YoutubeService.class);
		}

		@Bean
		YoutubeProperties youtubeProperties() {
			return org.mockito.Mockito.mock(YoutubeProperties.class);
		}
	}
}
