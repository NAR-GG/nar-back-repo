package com.toy.nar.app.appstore;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.toy.nar.app.appstore.AppStoreConnectClient.CustomerReview;
import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.domain.appstore.repository.AppStoreReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 앱스토어 신규 고객 리뷰를 디스코드로 알린다.
 *
 * <p>애플 웹훅에 리뷰 이벤트가 없어서 폴링이 유일한 방법이다({@link AppStoreConnectClient} 참고).
 * 리뷰는 하루 몇 건이라 주기가 짧을 이유가 없고, ASC 레이트리밋(시간당 수천)에도 무관하다.
 *
 * <p>첫 가동은 <b>발송 없이 씨딩만</b> 한다. 안 그러면 과거 리뷰 {@code limit} 건이 한꺼번에
 * 채널로 쏟아진다. 두 번째 폴링부터가 진짜 신규다.
 *
 * <p>ponytail: 페이지네이션을 따라가지 않는다. 30분에 한 번 최신 50건이면 유실이 없다.
 * 리뷰가 30분에 50건 넘게 들어오는 앱이 되면 그때 커서를 붙인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppStoreReviewMonitor {

	static final String PLATFORM_IOS = "IOS";

	private static final DateTimeFormatter KST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final AppStoreConnectClient client;
	private final AppStoreReviewRepository repository;
	private final NotificationService notificationService;

	@Value("${app-store.review-monitor.enabled:false}")
	private boolean enabled;

	@Value("${app-store.review-monitor.fetch-limit:50}")
	private int fetchLimit;

	@Scheduled(cron = "${app-store.review-monitor.cron:0 */30 * * * *}", zone = "Asia/Seoul")
	public void pollReviews() {
		if (!enabled || !client.isAvailable()) {
			return;
		}

		List<CustomerReview> reviews = client.fetchRecentReviews(fetchLimit);
		if (reviews.isEmpty()) {
			return;
		}

		boolean seeding = repository.isEmpty(PLATFORM_IOS);
		int notified = 0;
		// 응답은 최신순이다. 뒤에서부터 돌려 오래된 리뷰가 채널에 먼저 오게 한다.
		for (int i = reviews.size() - 1; i >= 0; i--) {
			CustomerReview review = reviews.get(i);
			boolean isNew = repository.markSeen(
					PLATFORM_IOS, review.id(), review.rating(), review.territory());
			if (!isNew || seeding) {
				continue;
			}
			notificationService.sendAppStoreReviewNotification(
					reviewTitle(review), reviewMessage(review), reviewColor(review.rating()));
			notified++;
		}

		if (seeding) {
			log.info("앱스토어 리뷰 모니터 첫 가동 — {}건 씨딩만 하고 발송은 건너뛴다", reviews.size());
		} else if (notified > 0) {
			log.info("앱스토어 신규 리뷰 {}건 알림", notified);
		}
	}

	static String reviewTitle(CustomerReview review) {
		return String.format("[앱스토어 리뷰] %s %d점", stars(review.rating()), review.rating());
	}

	static String reviewMessage(CustomerReview review) {
		String body = review.body() == null || review.body().isBlank() ? "(본문 없음)" : review.body();
		return String.format("**%s**\n```text\n%s\n```\n작성자 `%s` · 지역 `%s` · 작성 `%s`",
				review.title() == null || review.title().isBlank() ? "(제목 없음)" : review.title(),
				body,
				review.nickname(),
				review.territory(),
				toSeoul(review.createdDate()));
	}

	/**
	 * ASC 는 애플 본사 시간대 오프셋으로 준다(예: {@code 2026-08-30T05:25:54-07:00}).
	 * 그대로 실으면 알림을 읽을 때마다 시차를 손으로 더해야 해서 KST 로 옮긴다.
	 * 모양이 바뀌면 원문을 그대로 보낸다 — 형식 파싱 실패로 알림을 잃지 않는다.
	 */
	static String toSeoul(String isoOffsetDateTime) {
		try {
			return OffsetDateTime.parse(isoOffsetDateTime)
					.atZoneSameInstant(ZoneId.of("Asia/Seoul"))
					.format(KST_FORMAT);
		} catch (RuntimeException e) {
			return isoOffsetDateTime;
		}
	}

	/** 별점 1~2 는 즉시 대응 대상이라 빨강, 3 은 주황, 4~5 는 초록. */
	static String reviewColor(int rating) {
		if (rating <= 2) return "danger";
		if (rating == 3) return "warning";
		return "good";
	}

	static String stars(int rating) {
		int filled = Math.max(0, Math.min(5, rating));
		return "★".repeat(filled) + "☆".repeat(5 - filled);
	}
}
