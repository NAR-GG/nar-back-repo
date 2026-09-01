package com.toy.nar.app.appstore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.app.appstore.AppStoreConnectClient.CustomerReview;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.domain.appstore.repository.AppStoreReviewRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리뷰 폴링의 함정은 하나다 — 첫 가동에 과거 리뷰 전량이 채널로 쏟아지는 것.
 * 씨딩 판정과 신규 판정이 각자 제 일을 하는지 잠근다.
 */
class AppStoreReviewMonitorTest {

	/** 발송 기록을 메모리 Set 으로 흉내낸다. markSeen 은 "처음 본 것"에만 true. */
	private static class FakeRepository extends AppStoreReviewRepository {
		private final Set<String> seen = new HashSet<>();

		FakeRepository() {
			super(null);
		}

		@Override
		public boolean markSeen(String platform, String reviewId, Integer rating, String territory) {
			return seen.add(platform + ":" + reviewId);
		}

		@Override
		public boolean isEmpty(String platform) {
			return seen.isEmpty();
		}
	}

	private static class FakeNotificationService extends NotificationService {
		final List<String> sent = new ArrayList<>();

		FakeNotificationService() {
			super(null);
		}

		@Override
		public void sendAppStoreNotification(String title, String message, String color) {
			sent.add(title);
		}
	}

	private static class FakeClient extends AppStoreConnectClient {
		private List<CustomerReview> reviews = List.of();

		FakeClient() {
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

	private static CustomerReview review(String id, int rating) {
		return new CustomerReview(id, rating, "제목" + id, "본문" + id, "닉", "KR", "2026-09-01T00:00:00Z");
	}

	@Test
	@DisplayName("첫 가동은 씨딩만 — 과거 리뷰를 채널에 쏟지 않는다")
	void firstRunSeedsWithoutSending() {
		FakeClient client = new FakeClient();
		FakeNotificationService notifications = new FakeNotificationService();
		AppStoreReviewMonitor monitor = monitor(client, new FakeRepository(), notifications);
		client.reviews = List.of(review("c", 5), review("b", 3), review("a", 1));

		monitor.pollReviews();

		assertThat(notifications.sent).isEmpty();
	}

	@Test
	@DisplayName("두 번째 폴링부터 신규만 발송하고, 오래된 리뷰가 먼저 나간다")
	void secondRunSendsOnlyNewInChronologicalOrder() {
		FakeClient client = new FakeClient();
		FakeNotificationService notifications = new FakeNotificationService();
		AppStoreReviewMonitor monitor = monitor(client, new FakeRepository(), notifications);

		client.reviews = List.of(review("a", 5));
		monitor.pollReviews();               // 씨딩
		assertThat(notifications.sent).isEmpty();

		// 응답은 최신순이다: c 가 가장 새 리뷰.
		client.reviews = List.of(review("c", 1), review("b", 4), review("a", 5));
		monitor.pollReviews();

		assertThat(notifications.sent)
				.containsExactly("[앱스토어 리뷰] ★★★★☆ 4점", "[앱스토어 리뷰] ★☆☆☆☆ 1점");

		// 세 번째 폴링에 같은 응답이 와도 다시 보내지 않는다.
		monitor.pollReviews();
		assertThat(notifications.sent).hasSize(2);
	}

	@Test
	@DisplayName("별점 1~2 는 danger, 3 은 warning, 4~5 는 good")
	void colorByRating() {
		assertThat(AppStoreReviewMonitor.reviewColor(1)).isEqualTo("danger");
		assertThat(AppStoreReviewMonitor.reviewColor(2)).isEqualTo("danger");
		assertThat(AppStoreReviewMonitor.reviewColor(3)).isEqualTo("warning");
		assertThat(AppStoreReviewMonitor.reviewColor(5)).isEqualTo("good");
	}

	private static AppStoreReviewMonitor monitor(
			AppStoreConnectClient client, AppStoreReviewRepository repository, NotificationService notifications) {
		AppStoreReviewMonitor monitor = new AppStoreReviewMonitor(client, repository, notifications);
		ReflectionTestUtils.setField(monitor, "enabled", true);
		ReflectionTestUtils.setField(monitor, "fetchLimit", 50);
		return monitor;
	}
}
