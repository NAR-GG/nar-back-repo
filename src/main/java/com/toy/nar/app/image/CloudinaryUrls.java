package com.toy.nar.app.image;

import java.util.regex.Pattern;

/**
 * Cloudinary 딜리버리 URL 에 변환 파라미터를 끼운다.
 *
 * <p>포맷을 {@code f_auto} 가 아니라 {@code f_webp} 로 못박는다. {@code f_auto} 는 클라이언트가 보내는
 * {@code Accept} 헤더를 보고 포맷을 고르는데, Flutter 의 {@code dart:io HttpClient} 는
 * {@code Accept: image/webp} 를 붙이지 않아 원본 PNG 가 그대로 내려온다.
 * 같은 자산 실측 — f_auto 46,610B(PNG) vs f_webp 11,806B(webp), 약 4배 차이.
 * webp 는 iOS 14+ / Android 4.0+ 에서 모두 디코딩되므로 못박아도 안전하다.
 */
public final class CloudinaryUrls {

	/** 회원 아바타. 프로필 화면 @3x 까지 커버하는 정사각 크롭(g_auto = 피사체 기준). */
	public static final String AVATAR = "f_webp,q_auto,w_300,h_300,c_fill,g_auto";

	/** 선수 사진 — 앱 선수 목록·상세 카드에 충분한 폭. */
	public static final String PLAYER = "f_webp,q_auto,w_500,c_limit";

	/** 공지 본문 이미지 — 폰 화면(시안 375, @3x=1125). */
	public static final String NOTICE = "f_webp,q_auto,w_750,c_limit";

	private static final String MARKER = "/image/upload/";

	/**
	 * Cloudinary 변환 세그먼트 모양: {@code f_webp} 처럼 {@code 접두사_값} 을 콤마로 이은 것.
	 * 버전 세그먼트({@code v1785198594})와 폴더명({@code profiles})은 {@code _} 가 없어 걸리지 않는다.
	 */
	private static final Pattern TRANSFORM = Pattern.compile("[a-z]+_[^/,]+(,[a-z]+_[^/,]+)*");

	private CloudinaryUrls() {
	}

	/**
	 * {@code .../image/upload/[기존변환/]v123/public_id} → {@code .../image/upload/{transform}/v123/public_id}.
	 *
	 * <p>기존 변환 세그먼트가 있으면 갈아끼우고, 없으면 새로 끼운다. Cloudinary URL 이 아니면
	 * (lolesports·ddragon 직링크, {@code null}) 손대지 않고 그대로 돌려준다 — 호출부에서 분기하지 않아도 되게.
	 */
	public static String with(String url, String transform) {
		if (url == null) {
			return null;
		}
		int marker = url.indexOf(MARKER);
		if (marker < 0) {
			return url;
		}
		int restAt = marker + MARKER.length();
		String rest = url.substring(restAt);
		String[] head = rest.split("/", 2);
		if (head.length == 2 && TRANSFORM.matcher(head[0]).matches()) {
			rest = head[1];
		}
		return url.substring(0, restAt) + transform + "/" + rest;
	}
}
