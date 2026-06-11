package com.toy.nar.app.lolesports;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 리그 관련 상수를 중앙 관리하는 클래스
 */
public final class LeagueConstants {

    private LeagueConstants() {
        // 인스턴스화 방지
    }

    /**
     * 동기화 및 API 대상 리그 목록
     */
    public static final List<String> TARGET_LEAGUES = List.of(
            "LCK", "LPL", "LEC", "LCS", "LCP", "CBLOL", "MSI", "WORLDS", "FIRST_STAND");

    /**
     * 일정 조회 시 허용되는 리그 목록 (TARGET_LEAGUES + 추가 리그)
     */
    public static final Set<String> ALLOWED_LEAGUES = Set.of(
            "LCK", "LPL", "LCP", "LEC", "LCS", "CBLOL", "MSI", "WORLDS", "FIRST_STAND");

    /**
     * lolesports API의 리그 ID 매핑
     */
    public static final Map<String, String> LEAGUE_IDS = Map.ofEntries(
            Map.entry("LCK", "98767991310872058"),
            Map.entry("LPL", "98767991314006698"),
            Map.entry("LEC", "98767991302996019"),
            Map.entry("LCS", "98767991299243165"),
            Map.entry("LCP", "113476371197627891"),
            Map.entry("CBLOL", "98767991332355509"),
            Map.entry("FIRST_STAND", "113464388705111224"),
            Map.entry("WORLDS", "98767975604431411"),
            Map.entry("MSI", "98767991325878492"));

    /**
     * 리그별 기본 라이브 스트림 URL
     */
    public static final Map<String, String> LIVE_STREAM_URLS = Map.of(
            "LCK", "https://play.sooplive.co.kr/aflol",
            "LPL", "https://chzzk.naver.com/live/92b762ef6fac0cc8c68bc080868ad582",
            "LEC", "https://www.twitch.tv/lec",
            "LCS", "https://www.twitch.tv/lcs",
            "WORLDS", "https://www.twitch.tv/riotgames",
            "MSI", "https://www.twitch.tv/riotgames",
            "FIRST_STAND", "https://www.twitch.tv/riotgames");

    /**
     * 스트림 URL이 없는 리그의 기본 폴백 URL
     */
    public static final String DEFAULT_STREAM_URL = "https://play.sooplive.co.kr/aflol";

    /**
     * 리그에 해당하는 라이브 스트림 URL 반환
     */
    public static String getLiveStreamUrl(String leagueName) {
        if (leagueName == null) {
            return DEFAULT_STREAM_URL;
        }
        return LIVE_STREAM_URLS.getOrDefault(leagueName.toUpperCase(), DEFAULT_STREAM_URL);
    }
}
