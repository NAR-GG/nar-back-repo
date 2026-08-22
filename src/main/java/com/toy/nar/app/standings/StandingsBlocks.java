package com.toy.nar.app.standings;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 순위 집계에 넣을 "정규 스테이지" 경기인지 판별한다.
 *
 * <p>네이버 순위표가 플레이오프를 빼고 센다. LCK 2026 Split 2 로 확인했다 — 플레이오프 5경기를
 * 포함하면 GEN 이 21승 7패가 되는데 네이버는 19승 6패를 준다(정규 90경기 + Split 3 36경기 기준).
 * 그래서 우리도 정규만 집계해야 숫자가 맞는다.
 *
 * <p>판별은 {@code match_title} 앞부분의 블록명으로 한다. lolesports {@code blockName} 을 그대로
 * 넣은 값이고 {@code "13주 차 | DK vs GEN"} 형태다.
 *
 * <p><b>화이트리스트로 짠 이유</b> — 플레이오프를 빼는 블랙리스트 방식이면 모르는 라벨이 생겼을 때
 * 조용히 순위에 섞인다. LPL 의 "대표 선발전"처럼 예상 못 한 이름이 실제로 있다. 화이트리스트면
 * 모르는 라벨은 빠지고 경기 수가 모자라게 나와서 티가 난다.
 */
public final class StandingsBlocks {

	private StandingsBlocks() {
	}

	/** "1주 차", "13주 차" — LCK·LPL·LEC·LCS·CBLOL 정규 블록. */
	private static final Pattern WEEK_BLOCK = Pattern.compile("^\\d+주\\s*차");

	/**
	 * 주차 표기를 안 쓰는 정규 스테이지.
	 * KeSPA 는 조별리그라 "그룹", LCP 는 "스위스"다.
	 */
	private static final Set<String> NON_WEEK_REGULAR_BLOCKS = Set.of("그룹", "스위스");

	/**
	 * {@code match_title} 에서 블록명만 뗀다. 구분자가 없으면 제목 전체를 블록명으로 본다.
	 */
	public static String blockNameOf(String matchTitle) {
		if (matchTitle == null) {
			return "";
		}
		int sep = matchTitle.indexOf(" | ");
		return (sep < 0 ? matchTitle : matchTitle.substring(0, sep)).trim();
	}

	/** 이 경기가 정규 스테이지인가. */
	public static boolean isRegular(String matchTitle) {
		String block = blockNameOf(matchTitle);
		if (block.isEmpty()) {
			return false;
		}
		return WEEK_BLOCK.matcher(block).find() || NON_WEEK_REGULAR_BLOCKS.contains(block);
	}
}
