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
            "LCK", "LPL", "LEC", "LCS", "LCP", "CBLOL", "MSI", "WORLDS");

    /**
     * 일정 조회 시 허용되는 리그 목록 (TARGET_LEAGUES + 추가 리그)
     */
    public static final Set<String> ALLOWED_LEAGUES = Set.of(
            "LCK", "LPL", "LCP", "LEC", "LCS", "CBLOL", "MSI", "WORLDS");

    /**
     * 리그별 기본 라이브 스트림 URL
     */
    public static final Map<String, String> LIVE_STREAM_URLS = Map.of(
            "LCK", "https://play.sooplive.co.kr/aflol",
            "LPL", "https://chzzk.naver.com/live/92b762ef6fac0cc8c68bc080868ad582",
            "LEC", "https://www.twitch.tv/lec",
            "LCS", "https://www.twitch.tv/lcs",
            "WORLDS", "https://www.twitch.tv/riotgames",
            "MSI", "https://www.twitch.tv/riotgames");

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
