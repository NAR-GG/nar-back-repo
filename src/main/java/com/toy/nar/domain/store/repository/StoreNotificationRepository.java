package com.toy.nar.domain.store.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 앱 마켓 알림 발송 기록. 애플·플레이가 {@code platform} 으로 같은 표를 나눠 쓴다.
 *
 * <p>JPA 엔티티를 두지 않는다 — 하는 일이 {@code INSERT IGNORE} 뿐이고, 그건 JPA 로는
 * 네이티브 쿼리 없이 표현할 수 없다. 읽기도 "발송 이력이 아예 없나" 한 줄뿐이다.
 */
@Repository
@RequiredArgsConstructor
public class StoreNotificationRepository {

	public static final String PLATFORM_IOS = "IOS";
	public static final String PLATFORM_ANDROID = "ANDROID";

	private final JdbcTemplate jdbcTemplate;

	/**
	 * 리뷰를 발송 대상으로 찜한다.
	 *
	 * @return 처음 본 리뷰면 true. 이미 있으면 false — 호출측은 발송을 건너뛴다.
	 *         기록을 먼저 남기고 발송하므로, 발송이 실패하면 그 리뷰는 다시 오지 않는다.
	 *         반대로 하면(발송 후 기록) 기록 실패가 매 폴링마다 재발송으로 번지므로
	 *         "가끔 한 건 유실" 쪽을 택했다.
	 */
	public boolean markReviewSeen(String platform, String reviewId, Integer rating, String territory) {
		return jdbcTemplate.update(
				"INSERT IGNORE INTO store_review_notified (platform, review_id, rating, territory)"
						+ " VALUES (?, ?, ?, ?)",
				platform, reviewId, rating, territory) == 1;
	}

	/**
	 * 해당 플랫폼 리뷰 발송 이력이 비었는지.
	 *
	 * <p>첫 가동 때 과거 리뷰 수십 건이 한꺼번에 채널로 쏟아지는 걸 막는 씨딩 판정용이다.
	 */
	public boolean hasNoReviews(String platform) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM store_review_notified WHERE platform = ?", Integer.class, platform);
		return count == null || count == 0;
	}

	/**
	 * (버전, 상태) 쌍을 발송 대상으로 찜한다. 롤아웃 시작과 완료가 각각 한 번씩 나가게 한다.
	 *
	 * @return 처음 본 쌍이면 true.
	 */
	public boolean markReleaseSeen(String platform, String versionCode, String status) {
		return jdbcTemplate.update(
				"INSERT IGNORE INTO store_release_notified (platform, version_code, status) VALUES (?, ?, ?)",
				platform, versionCode, status) == 1;
	}

	/** 해당 플랫폼 출시 발송 이력이 비었는지. 리뷰와 같은 이유로 첫 가동은 씨딩만 한다. */
	public boolean hasNoReleases(String platform) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM store_release_notified WHERE platform = ?", Integer.class, platform);
		return count == null || count == 0;
	}
}
