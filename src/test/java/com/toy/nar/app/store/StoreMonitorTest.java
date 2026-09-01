package com.toy.nar.app.store;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.store.AppStoreConnectClient.CustomerReview;
import com.toy.nar.app.store.PlayConsoleClient.PlayRelease;
import com.toy.nar.app.store.PlayConsoleClient.PlayReview;
import com.toy.nar.domain.store.repository.StoreNotificationRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폴링 알림의 함정 둘을 잠근다.
 *
 * <ol>
 *   <li>첫 가동에 과거 항목 전량이 채널로 쏟아지는 것 — 씨딩 판정</li>
 *   <li>출시 dedupe 키에 status 가 빠져 "출시 시작"만 오고 "출시 완료"가 안 오는 것</li>
 * </ol>
 */
class StoreMonitorTest {

	/** 발송 기록을 메모리 Set 으로 흉내낸다. mark* 는 "처음 본 것"에만 true. */
	private static class FakeRepository extends StoreNotificationRepository {
		private final Set<String> reviews = new HashSet<>();
		private final Set<String> releases = new HashSet<>();

		FakeRepository() {
			super(null);
		}

		@Override
		public boolean markReviewSeen(String platform, String reviewId, Integer rating, String territory) {
			return reviews.add(platform + ":" + reviewId);
		}

		@Override
		public boolean hasNoReviews(String platform) {
			return reviews.isEmpty();
		}

		@Override
		public boolean markReleaseSeen(String platform, String versionCode, String status) {
			return releases.add(platform + ":" + versionCode + ":" + status);
		}

		@Override
		public boolean hasNoReleases(String platform) {
			return releases.isEmpty();
		}
	}

	private static class FakeNotificationService extends NotificationService {
		final List<String> reviewTitles = new ArrayList<>();
		final List<String> deployTitles = new ArrayList<>();

		FakeNotificationService() {
			super(null);
		}

		@Override
		public void sendStoreReviewNotification(String title, String message, String color) {
			reviewTitles.add(title);
		}

		@Override
		public void sendStoreDeployNotification(String title, String message, String color) {
			deployTitles.add(title);
		}
	}

	private static class FakeAppStoreClient extends AppStoreConnectClient {
		private List<CustomerReview> reviews = List.of();

		FakeAppStoreClient() {
			super(null);
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public List<CustomerReview> fetchRecentReviews(int limit) {
			return reviews;
		}
	}

	private static class FakePlayClient extends PlayConsoleClient {
		private List<PlayReview> reviews = List.of();
		private List<PlayRelease> releases = List.of();

		FakePlayClient() {
			super(null);
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public List<PlayReview> fetchRecentReviews(int limit) {
			return reviews;
		}

		@Override
		public List<PlayRelease> fetchProductionReleases() {
			return releases;
		}
	}

	// ---------- 앱스토어 리뷰 ----------

	@Test
	@DisplayName("앱스토어: 첫 폴링은 씨딩만 — 과거 리뷰를 채널에 쏟지 않는다")
	void appStoreFirstRunSeedsWithoutSending() {
		FakeAppStoreClient client = new FakeAppStoreClient();
		FakeNotificationService notifications = new FakeNotificationService();
		AppStoreReviewMonitor monitor = appStoreMonitor(client, new FakeRepository(), notifications);
		client.reviews = List.of(iosReview("c", 5), iosReview("b", 3), iosReview("a", 1));

		monitor.pollReviews();

		assertThat(notifications.reviewTitles).isEmpty();
	}

	@Test
	@DisplayName("앱스토어: 두 번째 폴링부터 신규만, 오래된 리뷰가 먼저 나간다")
	void appStoreSecondRunSendsOnlyNewInChronologicalOrder() {
		FakeAppStoreClient client = new FakeAppStoreClient();
		FakeNotificationService notifications = new FakeNotificationService();
		AppStoreReviewMonitor monitor = appStoreMonitor(client, new FakeRepository(), notifications);

		client.reviews = List.of(iosReview("a", 5));
		monitor.pollReviews();                               // 씨딩
		assertThat(notifications.reviewTitles).isEmpty();

		// 응답은 최신순이다: c 가 가장 새 리뷰.
		client.reviews = List.of(iosReview("c", 1), iosReview("b", 4), iosReview("a", 5));
		monitor.pollReviews();

		assertThat(notifications.reviewTitles)
				.containsExactly("[앱스토어 리뷰] ★★★★☆ 4점", "[앱스토어 리뷰] ★☆☆☆☆ 1점");

		monitor.pollReviews();                               // 같은 응답 재수신
		assertThat(notifications.reviewTitles).hasSize(2);
	}

	// ---------- 플레이 리뷰 ----------

	@Test
	@DisplayName("플레이: 씨딩 후 신규만 발송한다")
	void playReviewsSeedThenSend() {
		FakePlayClient client = new FakePlayClient();
		FakeNotificationService notifications = new FakeNotificationService();
		PlayStoreMonitor monitor = playMonitor(client, new FakeRepository(), notifications);

		client.reviews = List.of(playReview("a", 5));
		monitor.pollReviews();
		assertThat(notifications.reviewTitles).isEmpty();

		client.reviews = List.of(playReview("b", 2), playReview("a", 5));
		monitor.pollReviews();
		assertThat(notifications.reviewTitles).containsExactly("[플레이 리뷰] ★★☆☆☆ 2점");
	}

	// ---------- 플레이 출시 ----------

	@Test
	@DisplayName("플레이 출시: dedupe 키에 status 가 들어가 시작과 완료가 둘 다 나간다")
	void playReleaseNotifiesEachStatusOnce() {
		FakePlayClient client = new FakePlayClient();
		FakeNotificationService notifications = new FakeNotificationService();
		FakeRepository repository = new FakeRepository();
		PlayStoreMonitor monitor = playMonitor(client, repository, notifications);

		client.releases = List.of(new PlayRelease("1.0.15", "completed", 1.0, List.of("40")));
		monitor.pollReleases();                              // 씨딩
		assertThat(notifications.deployTitles).isEmpty();

		// 새 버전이 단계적 출시로 올라간다.
		client.releases = List.of(new PlayRelease("1.0.16", "inProgress", 0.1, List.of("41")));
		monitor.pollReleases();
		assertThat(notifications.deployTitles).containsExactly("[플레이 출시] 단계적 출시 진행 중");

		// 같은 상태로 다시 폴링해도 재발송하지 않는다.
		monitor.pollReleases();
		assertThat(notifications.deployTitles).hasSize(1);

		// 같은 버전이 완료로 넘어가면 한 번 더 나간다 — status 가 키에 있어서다.
		client.releases = List.of(new PlayRelease("1.0.16", "completed", 1.0, List.of("41")));
		monitor.pollReleases();
		assertThat(notifications.deployTitles)
				.containsExactly("[플레이 출시] 단계적 출시 진행 중", "[플레이 출시] 출시 완료");
	}

	@Test
	@DisplayName("플레이 출시: draft 는 알리지 않는다 — 콘솔에서 만들다 만 상태다")
	void playReleaseSkipsDraft() {
		FakePlayClient client = new FakePlayClient();
		FakeNotificationService notifications = new FakeNotificationService();
		FakeRepository repository = new FakeRepository();
		PlayStoreMonitor monitor = playMonitor(client, repository, notifications);
		// 씨딩을 끝내 둔다.
		repository.markReleaseSeen(StoreNotificationRepository.PLATFORM_ANDROID, "1", "completed");

		client.releases = List.of(new PlayRelease("1.0.17", "draft", 0.0, List.of("42")));
		monitor.pollReleases();

		assertThat(notifications.deployTitles).isEmpty();
	}

	@Test
	@DisplayName("halted 는 danger, completed 는 good")
	void releaseColor() {
		assertThat(PlayStoreMonitor.releaseColor("halted")).isEqualTo("danger");
		assertThat(PlayStoreMonitor.releaseColor("completed")).isEqualTo("good");
		assertThat(PlayStoreMonitor.releaseColor("inProgress")).isEqualTo("info");
	}

	// ---------- 공용 문구 ----------

	@Test
	@DisplayName("작성 시각을 KST 로 옮긴다 — ASC 는 애플 본사 오프셋, 플레이는 UTC 로 준다")
	void createdDateInSeoulTime() {
		assertThat(StoreNotifyText.toSeoul("2026-08-30T05:25:54-07:00")).isEqualTo("2026-08-30 21:25");
		assertThat(StoreNotifyText.toSeoul("2026-08-30T12:25:54Z")).isEqualTo("2026-08-30 21:25");
		// 모양이 바뀌면 원문 그대로 — 파싱 실패로 알림을 잃지 않는다.
		assertThat(StoreNotifyText.toSeoul("알 수 없음")).isEqualTo("알 수 없음");
	}

	@Test
	@DisplayName("별점 1~2 는 danger, 3 은 warning, 4~5 는 good")
	void ratingColor() {
		assertThat(StoreNotifyText.ratingColor(1)).isEqualTo("danger");
		assertThat(StoreNotifyText.ratingColor(2)).isEqualTo("danger");
		assertThat(StoreNotifyText.ratingColor(3)).isEqualTo("warning");
		assertThat(StoreNotifyText.ratingColor(5)).isEqualTo("good");
	}

	// ---------- 헬퍼 ----------

	private static CustomerReview iosReview(String id, int rating) {
		return new CustomerReview(id, rating, "제목" + id, "본문" + id, "닉", "KR", "2026-09-01T00:00:00Z");
	}

	private static PlayReview playReview(String id, int rating) {
		return new PlayReview(id, rating, "본문" + id, "작성자", "ko", "Pixel 8", "1.0.15", 1788000000L);
	}

	private static AppStoreReviewMonitor appStoreMonitor(
			AppStoreConnectClient client, StoreNotificationRepository repository, NotificationService notifications) {
		AppStoreReviewMonitor monitor = new AppStoreReviewMonitor(client, repository, notifications);
		ReflectionTestUtils.setField(monitor, "enabled", true);
		ReflectionTestUtils.setField(monitor, "fetchLimit", 50);
		return monitor;
	}

	private static PlayStoreMonitor playMonitor(
			PlayConsoleClient client, StoreNotificationRepository repository, NotificationService notifications) {
		PlayStoreMonitor monitor = new PlayStoreMonitor(client, repository, notifications);
		ReflectionTestUtils.setField(monitor, "reviewMonitorEnabled", true);
		ReflectionTestUtils.setField(monitor, "releaseMonitorEnabled", true);
		ReflectionTestUtils.setField(monitor, "fetchLimit", 50);
		return monitor;
	}
}
