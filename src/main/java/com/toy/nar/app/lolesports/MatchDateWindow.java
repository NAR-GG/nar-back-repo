package com.toy.nar.app.lolesports;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * {@code league_match.match_date} 조회 구간을 KST 하루/기간 기준으로 만든다.
 *
 * <p><b>match_date 는 오프셋 없는 UTC 벽시계다.</b> 동기화가
 * {@code LocalDateTime.parse("2026-08-21T10:00:00Z")} 로 파싱하면서 {@code Z} 를 버리기 때문이다
 * (LeagueMatchService.convertToEntity, 변수명도 matchDateUtc 다). 값은 UTC 인데 타입에는
 * 시간대가 없어서, 경계를 넘길 때마다 읽는 쪽 가정에 따라 9시간이 튄다.
 *
 * <p>실제로 세 번 터졌다:
 * <ul>
 *   <li>순위표 {@code dataThrough} 를 LocalDateTime 그대로 응답에 실어 앱이 로컬로 읽음(#448)</li>
 *   <li>일정 조회가 KST 날짜를 UTC 컬럼에 그대로 비교 — 담기던 구간이 KST 09:00~다음날 08:59 였다</li>
 *   <li>홈·v3 경기 목록, 월 캘린더, 연 단위 시즌 집계도 같은 모양</li>
 * </ul>
 * LCK(17·19시)는 우연히 어긋남 안에 들어와 안 드러났고, KST 새벽에 열리는 LEC·LCS·CBLOL 만
 * 하루 앞 날짜에 붙었다.
 *
 * <p>조회 경계를 만들 때는 반드시 이 클래스를 쓴다. 근본 해법은 엔티티를 {@code Instant} 로
 * 바꾸는 것이지만 마이그레이션이 필요해 별건이다 — 그때까지 어긋남을 한 곳에 가둬 둔다.
 */
public final class MatchDateWindow {

	/** 서비스 기준 시간대. 일정의 "하루"는 항상 이 기준으로 자른다. */
	public static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** match_date 컬럼이 담고 있는 시간대. */
	public static final ZoneId MATCH_DATE_ZONE = ZoneOffset.UTC;

	private MatchDateWindow() {
	}

	/** KST 벽시계 시각을 match_date 와 같은 기준으로 옮긴다. 하루 경계가 아닌 임의 시각용. */
	public static LocalDateTime toUtc(LocalDateTime kstWallClock) {
		return kstWallClock.atZone(KST).withZoneSameInstant(MATCH_DATE_ZONE).toLocalDateTime();
	}

	/** match_date 를 KST 로 읽는다. 날짜만 필요하면 {@link #toKstDate}, 시각은 여기서 꺼낸다. */
	public static ZonedDateTime toKst(LocalDateTime matchDate) {
		return matchDate.atZone(MATCH_DATE_ZONE).withZoneSameInstant(KST);
	}

	/** KST 하루의 시작을 match_date 와 같은 기준으로 옮긴 값(포함). */
	public static LocalDateTime startOfDay(LocalDate kstDay) {
		return toUtc(kstDay.atStartOfDay());
	}

	/** KST 하루의 끝을 match_date 와 같은 기준으로 옮긴 값(포함). 다음 날 시작 −1초다. */
	public static LocalDateTime endOfDay(LocalDate kstDay) {
		return startOfDay(kstDay.plusDays(1)).minusSeconds(1);
	}

	/**
	 * match_date 가 KST 로 며칠인지. 화면에서 날짜로 묶을 때는 이걸 써야 한다 —
	 * {@code matchDate.toLocalDate()} 는 UTC 날짜라 KST 새벽 경기가 하루 앞으로 묶인다.
	 */
	public static LocalDate toKstDate(LocalDateTime matchDate) {
		return toKst(matchDate).toLocalDate();
	}
}
