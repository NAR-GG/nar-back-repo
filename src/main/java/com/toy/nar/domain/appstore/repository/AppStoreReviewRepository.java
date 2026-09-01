package com.toy.nar.domain.appstore.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 앱스토어 리뷰 발송 기록.
 *
 * <p>JPA 엔티티를 두지 않는다 — 하는 일이 {@code INSERT IGNORE} 하나뿐이고, 그건 JPA 로는
 * 네이티브 쿼리 없이 표현할 수 없다. 읽기도 "발송 이력이 아예 없나" 한 줄뿐이다.
 */
@Repository
@RequiredArgsConstructor
public class AppStoreReviewRepository {

	private final JdbcTemplate jdbcTemplate;

	/**
	 * 리뷰를 발송 대상으로 찜한다.
	 *
	 * @return 처음 본 리뷰면 true. 이미 있으면 false — 호출측은 발송을 건너뛴다.
	 *         기록을 먼저 남기고 발송하므로, 발송이 실패하면 그 리뷰는 다시 오지 않는다.
	 *         반대로 하면(발송 후 기록) 기록 실패가 매 폴링마다 재발송으로 번지므로
	 *         "가끔 한 건 유실" 쪽을 택했다.
	 */
	public boolean markSeen(String platform, String reviewId, Integer rating, String territory) {
		return jdbcTemplate.update(
				"INSERT IGNORE INTO app_store_review (platform, review_id, rating, territory) VALUES (?, ?, ?, ?)",
				platform, reviewId, rating, territory) == 1;
	}

	/**
	 * 해당 플랫폼 발송 이력이 비었는지.
	 *
	 * <p>첫 가동 때 과거 리뷰 수십 건이 한꺼번에 채널로 쏟아지는 걸 막는 씨딩 판정용이다.
	 */
	public boolean isEmpty(String platform) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM app_store_review WHERE platform = ?", Integer.class, platform);
		return count == null || count == 0;
	}
}
