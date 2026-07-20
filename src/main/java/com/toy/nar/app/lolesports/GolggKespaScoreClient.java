package com.toy.nar.app.lolesports;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * gol.gg(Games of Legends) 토너먼트 매치리스트를 스크랩해 KeSPA Cup 게임 결과를 가져온다.
 *
 * <p>KeSPA Cup 은 Disney+ 글로벌 독점 중계라 Riot lolesports·네이버 e스포츠 어디에도
 * 인게임/스코어 데이터가 없다(2026-07-20 실측: livestats 204, 네이버 미커버). gol.gg 는
 * 경기 종료 후 스코어보드를 무료로 공개하므로, 종료-후 세트 스코어 보정 보조 소스로 쓴다.</p>
 *
 * <p>ponytail: KeSPA Cup 2026 전용 하드코딩(매치리스트 URL·팀명→코드 맵). 시즌이 바뀌면 URL 을,
 * 다른 리그로 확장하면 팀 맵을 일반화한다. 지금은 KeSPA 한 대회만 커버하면 되므로 최소로 둔다.</p>
 */
@Slf4j
@Component
public class GolggKespaScoreClient {

	private static final String MATCHLIST_URL =
			"https://gol.gg/tournament/tournament-matchlist/KeSPA%20Cup%202026/";

	private static final String USER_AGENT =
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Safari/605.1.15";

	/** "1 - 0" 형태의 게임 스코어 셀. 날짜("2026-07-20")·패치("16.14")는 매칭되지 않는다. */
	private static final Pattern SCORE = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");
	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	/**
	 * gol.gg 팀 풀네임(소문자) → 우리 lolesports 팀 코드. 이름에 이 토큰이 포함되면 매핑한다.
	 * KeSPA Cup 2026 참가 6팀 기준(2026-07-20 gol.gg 매치리스트로 검증).
	 */
	private static final Map<String, String> NAME_TOKEN_TO_CODE = Map.of(
			"kiwoom", "KRX",
			"rolster", "KT",
			"nongshim", "NS",
			"fearx", "BFX",
			"brion", "BRO",
			"soopers", "DNS");

	@Value("${lolesports.kespa.golgg-enabled:true}")
	private boolean enabled;

	/** 종료된(스코어가 찍힌) 게임 목록. 실패·비활성 시 빈 목록. */
	public List<GameRow> fetchCompletedGames() {
		if (!enabled) {
			return List.of();
		}
		try {
			Document doc = Jsoup.connect(MATCHLIST_URL)
					.userAgent(USER_AGENT)
					.timeout(5000)
					.get();
			return parse(doc.html());
		} catch (Exception e) {
			log.warn("gol.gg KeSPA 매치리스트 조회 실패: {}", e.getMessage());
			return List.of();
		}
	}

	/**
	 * gol.gg 매치리스트 HTML 에서 (좌팀코드, 우팀코드, 좌스코어, 우스코어, KST날짜) 행을 뽑는다.
	 * 스코어가 없는(예정) 행·헤더·매핑 안 되는 팀은 건너뛴다. static 이라 픽스처로 단위 테스트 가능.
	 */
	static List<GameRow> parse(String html) {
		Document doc = Jsoup.parse(html);
		List<GameRow> out = new ArrayList<>();
		for (Element tr : doc.select("tr")) {
			Elements tds = tr.select("td");
			if (tds.size() < 4) {
				continue;
			}
			int scoreIdx = -1;
			Matcher score = null;
			for (int i = 0; i < tds.size(); i++) {
				Matcher m = SCORE.matcher(tds.get(i).text().trim());
				if (m.matches()) {
					scoreIdx = i;
					score = m;
					break;
				}
			}
			// 스코어 셀은 좌/우 팀 이름 셀 사이에 있어야 한다.
			if (scoreIdx < 1 || scoreIdx + 1 >= tds.size()) {
				continue;
			}
			String leftCode = toCode(tds.get(scoreIdx - 1).text());
			String rightCode = toCode(tds.get(scoreIdx + 1).text());
			if (leftCode == null || rightCode == null || leftCode.equals(rightCode)) {
				continue;
			}
			LocalDate date = null;
			for (Element td : tds) {
				String t = td.text().trim();
				if (DATE.matcher(t).matches()) {
					date = LocalDate.parse(t);
				}
			}
			if (date == null) {
				continue;
			}
			out.add(new GameRow(
					leftCode, rightCode,
					Integer.parseInt(score.group(1)),
					Integer.parseInt(score.group(2)),
					date));
		}
		return out;
	}

	/** gol.gg 팀 풀네임 → 우리 코드. 매핑 없으면 null. */
	static String toCode(String golggName) {
		if (golggName == null) {
			return null;
		}
		String lower = golggName.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> e : NAME_TOKEN_TO_CODE.entrySet()) {
			if (lower.contains(e.getKey())) {
				return e.getValue();
			}
		}
		return null;
	}

	/** gol.gg 한 게임 행. 스코어는 항상 승자 1 / 패자 0 (단판) 또는 세트 승패. */
	public record GameRow(String leftCode, String rightCode, int leftScore, int rightScore, LocalDate dateKst) {
	}
}
