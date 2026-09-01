package com.toy.nar.app.store;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.toy.nar.app.data.source.NotificationService;
import com.toy.nar.app.store.PlayConsoleClient.PlayRelease;
import com.toy.nar.app.store.PlayConsoleClient.PlayReview;
import com.toy.nar.domain.store.repository.StoreNotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 플레이스토어 신규 리뷰와 프로덕션 출시를 디스코드로 알린다.
 *
 * <p>애플과 달리 웹훅이 없어 둘 다 폴링이다. 리뷰 모니터와 출시 모니터를 한 클래스에 둔 이유는
 * 자격증명·플래그·플랫폼 상수가 같아서다 — 쪼개도 늘어나는 건 보일러플레이트뿐이다.
 *
 * <p><b>구글은 리뷰를 최근 7일치만 준다.</b> 폴링 주기가 7일을 넘기면 그 사이 리뷰는 영구히
 * 못 받는다(Play Console CSV 로만 남는다). 기본 30분이라 여유는 충분하다.
 *
 * <p>리뷰·출시 모두 첫 가동은 <b>발송 없이 씨딩만</b> 한다. 안 그러면 과거 리뷰와 이미 끝난
 * 롤아웃이 채널로 쏟아진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayStoreMonitor {

	private static final String PLATFORM = StoreNotificationRepository.PLATFORM_ANDROID;

	private final PlayConsoleClient client;
	private final StoreNotificationRepository repository;
	private final NotificationService notificationService;

	@Value("${play-store.review-monitor.enabled:false}")
	private boolean reviewMonitorEnabled;

	@Value("${play-store.review-monitor.fetch-limit:50}")
	private int fetchLimit;

	@Value("${play-store.release-monitor.enabled:false}")
	private boolean releaseMonitorEnabled;

	@Scheduled(cron = "${play-store.review-monitor.cron:0 5,35 * * * *}", zone = "Asia/Seoul")
	public void pollReviews() {
		if (!reviewMonitorEnabled || !client.isAvailable()) {
			return;
		}

		List<PlayReview> reviews = client.fetchRecentReviews(fetchLimit);
		if (reviews.isEmpty()) {
			return;
		}

		boolean seeding = repository.hasNoReviews(PLATFORM);
		int notified = 0;
		// 응답은 최신순이다. 뒤에서부터 돌려 오래된 리뷰가 채널에 먼저 오게 한다.
		for (int i = reviews.size() - 1; i >= 0; i--) {
			PlayReview review = reviews.get(i);
			boolean isNew = repository.markReviewSeen(PLATFORM, review.id(), review.rating(), review.language());
			if (!isNew || seeding) {
				continue;
			}
			notificationService.sendStoreReviewNotification(
					reviewTitle(review), reviewMessage(review), StoreNotifyText.ratingColor(review.rating()));
			notified++;
		}

		if (seeding) {
			log.info("플레이 리뷰 모니터 첫 가동 — {}건 씨딩만 하고 발송은 건너뛴다", reviews.size());
		} else if (notified > 0) {
			log.info("플레이 신규 리뷰 {}건 알림", notified);
		}
	}

	/**
	 * 프로덕션 트랙 폴링. edit 를 열고 닫는 왕복이라 리뷰보다 주기를 길게 잡는다.
	 *
	 * <p>여기서 잡히는 건 "출시됨"까지다. <b>심사 중·거부는 플레이 API 에 없다</b> —
	 * 그건 구글 이메일로만 온다.
	 */
	@Scheduled(cron = "${play-store.release-monitor.cron:0 15 * * * *}", zone = "Asia/Seoul")
	public void pollReleases() {
		if (!releaseMonitorEnabled || !client.isAvailable()) {
			return;
		}

		List<PlayRelease> releases = client.fetchProductionReleases();
		if (releases.isEmpty()) {
			return;
		}

		boolean seeding = repository.hasNoReleases(PLATFORM);
		int notified = 0;
		for (PlayRelease release : releases) {
			// draft 는 우리가 콘솔에서 만들다 만 상태다. 알릴 사건이 아니다.
			if ("draft".equals(release.status())) {
				continue;
			}
			boolean isNew = repository.markReleaseSeen(PLATFORM, release.versionKey(), release.status());
			if (!isNew || seeding) {
				continue;
			}
			notificationService.sendStoreDeployNotification(
					releaseTitle(release), releaseMessage(release), releaseColor(release.status()));
			notified++;
		}

		if (seeding) {
			log.info("플레이 출시 모니터 첫 가동 — {}건 씨딩만 하고 발송은 건너뛴다", releases.size());
		} else if (notified > 0) {
			log.info("플레이 출시 상태 변경 {}건 알림", notified);
		}
	}

	static String reviewTitle(PlayReview review) {
		return String.format("[플레이 리뷰] %s %d점",
				StoreNotifyText.stars(review.rating()), review.rating());
	}

	static String reviewMessage(PlayReview review) {
		return String.format("```text\n%s\n```\n작성자 `%s` · 기기 `%s` · 앱 `%s` · 작성 `%s`",
				StoreNotifyText.orPlaceholder(review.text(), "(본문 없음)"),
				StoreNotifyText.orPlaceholder(review.authorName(), "(익명)"),
				StoreNotifyText.orPlaceholder(review.device(), "-"),
				StoreNotifyText.orPlaceholder(review.appVersion(), "-"),
				review.lastModifiedEpochSeconds() > 0
						? StoreNotifyText.toSeoul(Instant.ofEpochSecond(review.lastModifiedEpochSeconds()).toString())
						: "-");
	}

	static String releaseTitle(PlayRelease release) {
		String label = switch (release.status()) {
			case "inProgress" -> "단계적 출시 진행 중";
			case "completed" -> "출시 완료";
			case "halted" -> "출시 중단";
			default -> release.status();
		};
		return "[플레이 출시] " + label;
	}

	static String releaseMessage(PlayRelease release) {
		return String.format("```text\n버전 코드: %s\n릴리스명: %s\n상태: %s\n배포 비율: %.0f%%\n```",
				StoreNotifyText.orPlaceholder(release.versionKey(), "-"),
				StoreNotifyText.orPlaceholder(release.name(), "-"),
				release.status(),
				release.userFraction() * 100);
	}

	/** halted 만 눈에 띄게 한다 — 롤아웃을 멈춘 건 사고이거나 사고 대응이다. */
	static String releaseColor(String status) {
		return switch (status) {
			case "halted" -> "danger";
			case "completed" -> "good";
			default -> "info";
		};
	}
}
