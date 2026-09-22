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
            "LCK", "LPL", "LEC", "LCS", "LCP", "CBLOL", "MSI", "WORLDS", "FIRST_STAND", "EWC", "KESPA",
            "ASIAN_GAMES");

    /**
     * 일정 조회 시 허용되는 리그 목록 (TARGET_LEAGUES + 추가 리그)
     */
    public static final Set<String> ALLOWED_LEAGUES = Set.of(
            "LCK", "LPL", "LCP", "LEC", "LCS", "CBLOL", "MSI", "WORLDS", "FIRST_STAND", "EWC", "KESPA",
            "ASIAN_GAMES");

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
            Map.entry("EWC", "116838530616006090"),
            Map.entry("KESPA", "116929044967296666"),
            Map.entry("ASIAN_GAMES", "117228885404001005"));

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
     * 스트림 링크를 의도적으로 제공하지 않는 리그. SOOP 기본 폴백도 적용하지 않는다.
     * KeSPA Cup 은 Disney+ 글로벌 독점이라 앱에서 링크를 노출할 대체 채널이 없다.
     * 아시안게임은 주최가 OCA 라 Riot 계열 중계 채널이 없다 — 국내 중계처가 확정되면
     * LIVE_STREAM_URLS 에 넣고 여기서 빼면 된다. 그때까지 SOOP 폴백은 틀린 링크다.
     */
    private static final Set<String> NO_STREAM_LEAGUES = Set.of("KESPA", "ASIAN_GAMES");

    /**
     * 아시안게임 국가팀 로고 교체본.
     *
     * <p>lolesports 가 준 원본은 국기가 아니라 검은 테두리 안에 국가 코드를 쓴 임시 PNG 다
     * (파일명이 {@code ..._kor1-Photoroom.png} — 배경 제거 앱 출력물을 그대로 올렸다).
     * 앱 배경이 {@code #141517} 이라 검은 로고가 거의 보이지 않아 국기로 바꾼다.
     *
     * <p>호스팅은 우리 서버다. {@code src/main/resources/static/images/flags/} 에 넣고
     * SecurityConfig 가 이미 열어 둔 {@code /images/**} 로 서빙한다 — Cloudinary 업로드도
     * 외부 CDN 의존도 생기지 않는다. 호스트가 {@code ImageCdn} 의 fetch 화이트리스트 밖이라
     * 래핑 없이 그대로 나간다(이미 200px 로 줄여 둬서 변환도 불필요하다).
     *
     * <p>키는 팀 코드다. 2026-09-23 기준 lolesports 전체 1586팀 중 이 여덟 코드를 쓰는 팀은
     * 아시안게임 국가팀뿐이라 다른 리그와 충돌하지 않는다.
     */
    private static final Map<String, String> NATIONAL_TEAM_IMAGES = Map.of(
            "KOR", flagUrl("kor"),
            "TPE", flagUrl("tpe"),
            "HKG", flagUrl("hkg"),
            "IND", flagUrl("ind"),
            "KSA", flagUrl("ksa"),
            "VIE", flagUrl("vie"),
            "MAS", flagUrl("mas"),
            "UAE", flagUrl("uae"));

    private static String flagUrl(String code) {
        return "https://api.nar.kr/images/flags/" + code + ".png";
    }

    /**
     * 팀 코드에 해당하는 교체 로고. 교체 대상이 아니면 {@code null} 을 돌려주므로
     * 호출부는 원본 URL 을 그대로 쓰면 된다.
     */
    public static String nationalTeamImage(String teamCode) {
        if (teamCode == null || teamCode.isBlank()) {
            return null;
        }
        return NATIONAL_TEAM_IMAGES.get(teamCode.trim().toUpperCase());
    }

    /**
     * lolesports API 리그 slug → 내부 리그명. slug이 리그 코드와 다른 리그(EWC: ewc_lol, KESPA: kespa_cup)만 보정한다.
     */
    public static String fromApiSlug(String slug) {
        String upper = slug == null ? "" : slug.trim().toUpperCase();
        if ("EWC_LOL".equals(upper)) {
            return "EWC";
        }
        if ("KESPA_CUP".equals(upper)) {
            return "KESPA";
        }
        return upper;
    }

    /**
     * 리그에 해당하는 라이브 스트림 URL 반환
     */
    public static String getLiveStreamUrl(String leagueName) {
        if (leagueName == null) {
            return DEFAULT_STREAM_URL;
        }
        String upper = leagueName.toUpperCase();
        if (NO_STREAM_LEAGUES.contains(upper)) {
            return null;
        }
        return LIVE_STREAM_URLS.getOrDefault(upper, DEFAULT_STREAM_URL);
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
            // KeSPA Cup(Disney+ 독점)은 링크를 별도로 두지 않고 기본 폴백에 맡긴다.
            "EWC", List.of(CHZZK_EWC, TWITCH_EWC));

    /**
     * 리그의 중계 채널 목록 반환. 매핑이 없으면 SOOP 단일 링크로 폴백.
     */
    public static List<StreamLink> getStreamLinks(String leagueName) {
        if (leagueName == null) {
            return List.of(SOOP_AFLOL);
        }
        String upper = leagueName.toUpperCase();
        if (NO_STREAM_LEAGUES.contains(upper)) {
            return List.of();
        }
        return STREAM_LINKS.getOrDefault(upper, List.of(SOOP_AFLOL));
    }
}
