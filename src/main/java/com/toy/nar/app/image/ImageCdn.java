package com.toy.nar.app.image;

import java.net.URI;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.toy.nar.config.CloudinaryProperties;

import lombok.RequiredArgsConstructor;

/**
 * Riot 계열 원본 이미지를 Cloudinary fetch 로 감싸 최적화본을 내려준다.
 *
 * <p>Riot CDN 은 캐시는 잘 되지만 포맷/크기 변환이 없어 원본 PNG 를 그대로 뱉는다. fetch 는 업로드 없이
 * 원본 URL 을 그대로 두고 Cloudinary 가 대신 받아 변환·캐시하게 하는 방식이라, 우리 쪽에 저장소도
 * 갱신 감시 파이프라인도 생기지 않는다. 실측(전 → 후):
 * 팀 로고 130,938B → 7,998B / 선수 276,907B → 14,660B / 챔피언 25,644B → 2,042B /
 * 스플래시 91,088B(1280×720 가로) → 17,492B(400×600 세로).
 *
 * <p>래핑은 <b>쓰기 시점</b>에 걸어 DB 에 fetch URL 을 저장한다. 읽기 지점이 88곳이고 그 중에는
 * native 쿼리 투영·Elasticsearch 인덱스 문서·Discord 웹훅 페이로드처럼 DTO 직렬화를 거치지 않는
 * 경로가 섞여 있어, 읽기 쪽에서 감싸면 일부만 최적화되는 반쪽 상태가 된다.
 *
 * <p>원본 URL 은 fetch URL 안에 그대로 남으므로 되돌릴 수 있다(롤백은 {@code CLOUDINARY_CDN_ENABLED=false}
 * + 언랩 SQL).
 */
@Component
@RequiredArgsConstructor
public class ImageCdn {

	/** 팀 로고 — 경기 카드·목록. */
	public static final String TEAM = "f_webp,q_auto,w_200,c_limit";

	/** 선수 사진 — 선수 목록·상세 카드. */
	public static final String PLAYER = "f_webp,q_auto,w_500,c_limit";

	/** 챔피언 스퀘어 아이콘. */
	public static final String CHAMPION = "f_webp,q_auto,w_128,c_limit";

	/**
	 * 챔피언 픽 카드용 세로 이미지. 원본(CommunityDragon centered splash)은 1280×720 <b>가로</b>라
	 * 지금까지 클라이언트가 세로로 잘라 쓰고 있었다 — 보이지도 않는 좌우를 매번 받았다는 뜻이다.
	 * 크롭을 서버로 옮긴다({@code g_auto} = 피사체 기준).
	 */
	public static final String SPLASH = "f_webp,q_auto,w_400,h_600,c_fill,g_auto";

	/**
	 * Cloudinary 콘솔의 Allowed fetch domains 와 같은 목록. 여기 없는 호스트를 감싸면 401 이 내려오므로
	 * (실측 확인), 목록 밖 URL 은 손대지 않고 그대로 통과시킨다 — 회원/선수 업로드본(res.cloudinary.com),
	 * JAR 내부 경로({@code /images/players/...}), 유튜브 썸네일이 여기 해당한다.
	 */
	private static final Set<String> FETCHABLE_HOSTS = Set.of(
			"static.lolesports.com",
			"ddragon.leagueoflegends.com",
			"cdn.communitydragon.org");

	private static final String FETCH_MARKER = "/image/fetch/";

	private final CloudinaryProperties properties;

	public String team(String originUrl) {
		return fetch(originUrl, TEAM);
	}

	public String player(String originUrl) {
		return fetch(originUrl, PLAYER);
	}

	public String champion(String originUrl) {
		return fetch(originUrl, CHAMPION);
	}

	public String splash(String originUrl) {
		return fetch(originUrl, SPLASH);
	}

	/**
	 * 감쌀 수 없거나 감쌀 필요가 없으면 원본을 그대로 돌려준다. 호출부가 분기하지 않아도 되게 하고,
	 * 두 번 감싸도 결과가 같도록(멱등) 만들어 동기화 dirty 검사가 원본/래핑을 섞어 봐도 안전하게 한다.
	 */
	public String fetch(String originUrl, String transform) {
		if (originUrl == null || !enabled() || originUrl.contains(FETCH_MARKER) || !isFetchable(originUrl)) {
			return originUrl;
		}
		return "https://res.cloudinary.com/" + properties.getCloudName() + FETCH_MARKER + transform + "/" + originUrl;
	}

	/** 원본 URL 을 복원한다 — 언랩 SQL 과 같은 규칙. 래핑되지 않은 값은 그대로 돌려준다. */
	public static String origin(String url) {
		if (url == null) {
			return null;
		}
		int marker = url.indexOf(FETCH_MARKER);
		if (marker < 0) {
			return url;
		}
		int schemeAt = url.indexOf("/https://", marker);
		return schemeAt < 0 ? url : url.substring(schemeAt + 1);
	}

	private boolean enabled() {
		return properties.isCdnEnabled()
				&& properties.getCloudName() != null
				&& !properties.getCloudName().isBlank();
	}

	private static boolean isFetchable(String url) {
		try {
			// 상대 경로(JAR 내부 /images/players/...)는 host 가 null 이고, Set.of 는 contains(null) 에 NPE 를 던진다.
			String host = URI.create(url).getHost();
			return host != null && FETCHABLE_HOSTS.contains(host);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
