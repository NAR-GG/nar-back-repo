package com.toy.nar.app.store;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 마켓 알림 문구 조립에서 애플·플레이가 똑같이 쓰는 조각들. */
final class StoreNotifyText {

	private static final DateTimeFormatter KST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private StoreNotifyText() {
	}

	static String stars(int rating) {
		int filled = Math.max(0, Math.min(5, rating));
		return "★".repeat(filled) + "☆".repeat(5 - filled);
	}

	/** 별점 1~2 는 즉시 대응 대상이라 빨강, 3 은 주황, 4~5 는 초록. */
	static String ratingColor(int rating) {
		if (rating <= 2) return "danger";
		if (rating == 3) return "warning";
		return "good";
	}

	/**
	 * ISO-8601 시각을 KST 로 옮긴다.
	 *
	 * <p>ASC 는 애플 본사 오프셋으로 주고(예: {@code 2026-08-30T05:25:54-07:00}) 플레이는 UTC 로 준다.
	 * 그대로 실으면 알림을 읽을 때마다 시차를 손으로 더해야 한다.
	 * 모양이 바뀌면 원문을 그대로 돌려준다 — 형식 파싱 실패로 알림을 잃지 않는다.
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

	/** 비어 있으면 자리표시자. 디스코드 embed 에서 빈 줄로 보이는 것을 막는다. */
	static String orPlaceholder(String value, String placeholder) {
		return (value == null || value.isBlank()) ? placeholder : value;
	}
}
