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
            "LCK", "LPL", "LEC", "LCS", "LCP", "CBLOL", "MSI", "WORLDS", "FIRST_STAND", "EWC");

    /**
     * 일정 조회 시 허용되는 리그 목록 (TARGET_LEAGUES + 추가 리그)
     */
    public static final Set<String> ALLOWED_LEAGUES = Set.of(
            "LCK", "LPL", "LCP", "LEC", "LCS", "CBLOL", "MSI", "WORLDS", "FIRST_STAND", "EWC");

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
            Map.entry("MSI", "98767991325878492"),
            Map.entry("EWC", "116838530616006090"));

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
            "FIRST_STAND", "https://www.twitch.tv/riotgames",
            // EWC 2026 한국어 중계는 치지직 독점. "EWC 공식 채널B"가 LoL 담당(2026-07-18 라이브 방제로 검증).
            // 대회 측 편성이 바뀌면 공식 채널 A~F 중 LoL 방제를 트는 채널로 교체 필요.
            "EWC", "https://chzzk.naver.com/live/2b753bd5325fc34bba16d66659c67aa2");

    /**
     * 스트림 URL이 없는 리그의 기본 폴백 URL
     */
    public static final String DEFAULT_STREAM_URL = "https://play.sooplive.co.kr/aflol";

    /**
     * lolesports API 리그 slug → 내부 리그명. slug이 리그 코드와 다른 리그(EWC: ewc_lol)만 보정한다.
     */
    public static String fromApiSlug(String slug) {
        String upper = slug == null ? "" : slug.trim().toUpperCase();
        return "EWC_LOL".equals(upper) ? "EWC" : upper;
    }

    /**
     * 리그에 해당하는 라이브 스트림 URL 반환
     */
    public static String getLiveStreamUrl(String leagueName) {
        if (leagueName == null) {
            return DEFAULT_STREAM_URL;
        }
        return LIVE_STREAM_URLS.getOrDefault(leagueName.toUpperCase(), DEFAULT_STREAM_URL);
    }

    /**
     * 라이브 중계 채널 링크. 앱에서 유저가 플랫폼을 골라 시청할 수 있게 리그당 여러 개를 내려준다.
     */
    public record StreamLink(String provider, String label, String description, String url) {
    }

    /** 치지직 LCK 공식 채널 — 2026~ 네이버가 LCK·글로벌 대회 한국어 중계권 보유. */
    private static final StreamLink CHZZK_LCK = new StreamLink(
            "chzzk", "치지직", "LCK 공식 채널 · 한국어",
            "https://chzzk.naver.com/9381e7d6816e6d915a44a13c0195b202");

    /** SOOP(아프리카TV) 공식 롤 중계 채널. */
    private static final StreamLink SOOP_AFLOL = new StreamLink(
            "soop", "SOOP", "aflol · 한국어",
            "https://play.sooplive.co.kr/aflol");

    private static final StreamLink CHZZK_LPL = new StreamLink(
            "chzzk", "치지직", "LPL 한국어 중계",
            "https://chzzk.naver.com/live/92b762ef6fac0cc8c68bc080868ad582");

    /** EWC 2026 한국어 공식 치지직 — "EWC 공식 채널B"가 LoL 담당(2026-07-18 라이브 방제로 검증). 편성 변경 시 A~F 중 LoL 채널로 교체. */
    private static final StreamLink CHZZK_EWC = new StreamLink(
            "chzzk", "치지직", "EWC 공식 · 한국어",
            "https://chzzk.naver.com/live/2b753bd5325fc34bba16d66659c67aa2");

    /** EWC 2026 영어 공식 Twitch. */
    private static final StreamLink TWITCH_EWC = new StreamLink(
            "twitch", "Twitch", "EWC 공식 · 영어",
            "https://www.twitch.tv/ewclol2026");

    /**
     * 리그별 중계 채널 목록. 한국어 중계가 복수인 리그(LCK·국제전)는 치지직/SOOP 둘 다 내려주고,
     * 그 외 리그는 기존 단일 링크를 유지한다.
     */
    public static final Map<String, List<StreamLink>> STREAM_LINKS = Map.of(
            "LCK", List.of(CHZZK_LCK, SOOP_AFLOL),
            "MSI", List.of(CHZZK_LCK, SOOP_AFLOL),
            "WORLDS", List.of(CHZZK_LCK, SOOP_AFLOL),
            "FIRST_STAND", List.of(CHZZK_LCK, SOOP_AFLOL),
            "LPL", List.of(CHZZK_LPL),
            "LEC", List.of(new StreamLink("twitch", "Twitch", "LEC 공식", "https://www.twitch.tv/lec")),
            "LCS", List.of(new StreamLink("twitch", "Twitch", "LCS 공식", "https://www.twitch.tv/lcs")),
            "EWC", List.of(CHZZK_EWC, TWITCH_EWC));

    /**
     * 리그의 중계 채널 목록 반환. 매핑이 없으면 SOOP 단일 링크로 폴백.
     */
    public static List<StreamLink> getStreamLinks(String leagueName) {
        if (leagueName == null) {
            return List.of(SOOP_AFLOL);
        }
        return STREAM_LINKS.getOrDefault(leagueName.toUpperCase(), List.of(SOOP_AFLOL));
    }
}
