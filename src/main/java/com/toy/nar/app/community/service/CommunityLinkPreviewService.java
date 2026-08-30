package com.toy.nar.app.community.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.toy.nar.app.community.dto.CommunityDtos.LinkPreviewResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;

import lombok.extern.slf4j.Slf4j;

/**
 * 링크 프리뷰 — 서버가 대상 페이지의 OG 태그를 긁어 스냅샷을 돌려준다. 앱은 이 결과를
 * link 블록에 저장하므로 조회 때마다 재크롤하지 않는다(캐시는 작성 중 중복 호출용).
 *
 * <p><b>SSRF 가드</b> — 서버가 임의 URL 을 대신 fetch 하는 API 라 필수다:
 * http(s)만 · 호스트를 리졸브해 사설/루프백/링크로컬 IP 차단 · 리다이렉트는 수동으로
 * 최대 3회(매 hop 재검증) · 타임아웃 3초 · 응답 본문 512KB 상한.
 * ponytail: 리졸브-후-검증이라 DNS 리바인딩 창이 이론상 남는다 — 커넥터 레벨 IP 고정은
 * 악용 신호가 보이면 올린다.</p>
 *
 * <p>긁기 실패(타임아웃·비 HTML·OG 없음)는 예외가 아니다 — title 이하가 null 인 응답을
 * 돌려주고 앱이 맨 링크 카드로 그린다. 예외(400)는 URL 자체가 부적합할 때만.</p>
 */
@Slf4j
@Service
public class CommunityLinkPreviewService {

	private static final int MAX_REDIRECTS = 3;
	private static final int MAX_BODY_BYTES = 512 * 1024;
	private static final int MAX_URL_LENGTH = 500;
	private static final int MAX_TITLE = 200;
	private static final int MAX_DESCRIPTION = 300;
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	private final HttpClient httpClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER) // 리다이렉트마다 IP 재검증하려고 수동 추적
			.connectTimeout(TIMEOUT)
			.build();

	@Cacheable(value = "communityLinkPreview", sync = true)
	public LinkPreviewResponse preview(String url) {
		URI uri = requireSafeUri(url);
		try {
			String html = fetchWithManualRedirects(uri);
			if (html == null) {
				return empty(url);
			}
			Document doc = Jsoup.parse(html, uri.toString());
			String title = firstNonBlank(meta(doc, "og:title"), doc.title());
			String description = firstNonBlank(meta(doc, "og:description"),
					attrContent(doc, "meta[name=description]"));
			String imageUrl = meta(doc, "og:image");
			String siteName = meta(doc, "og:site_name");
			if (imageUrl != null && (imageUrl.length() > MAX_URL_LENGTH || !isHttp(imageUrl))) {
				imageUrl = null;
			}
			return new LinkPreviewResponse(url, cut(title, MAX_TITLE), cut(description, MAX_DESCRIPTION),
					imageUrl, cut(siteName, MAX_TITLE));
		} catch (IOException | InterruptedException | RuntimeException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.info("[community] link-preview 실패 url={} : {}", url, e.toString());
			return empty(url);
		}
	}

	private String fetchWithManualRedirects(URI uri) throws IOException, InterruptedException {
		URI current = uri;
		for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
			requireSafeUri(current.toString()); // 리다이렉트 목적지도 매번 검증
			HttpRequest request = HttpRequest.newBuilder(current)
					.timeout(TIMEOUT)
					.header("User-Agent", "Mozilla/5.0 (compatible; WardingBot/1.0; +https://nar.kr)")
					.header("Accept", "text/html")
					.GET()
					.build();
			HttpResponse<InputStream> response = httpClient.send(request,
					HttpResponse.BodyHandlers.ofInputStream());
			int status = response.statusCode();
			if (status >= 300 && status < 400) {
				String location = response.headers().firstValue("Location").orElse(null);
				response.body().close();
				if (location == null) {
					return null;
				}
				current = current.resolve(location);
				continue;
			}
			if (status != 200) {
				response.body().close();
				return null;
			}
			String contentType = response.headers().firstValue("Content-Type").orElse("");
			if (!contentType.toLowerCase(java.util.Locale.ROOT).contains("text/html")) {
				response.body().close();
				return null;
			}
			try (InputStream in = response.body()) {
				byte[] bytes = in.readNBytes(MAX_BODY_BYTES);
				return new String(bytes, StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	/** http(s) + 공인 IP 만 통과. 실패는 400 — URL 자체가 부적합한 요청이다. */
	private static URI requireSafeUri(String url) {
		if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH || !isHttp(url)) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		URI uri = URI.create(url.trim());
		String host = uri.getHost();
		if (host == null) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		try {
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (address.isLoopbackAddress() || address.isSiteLocalAddress()
						|| address.isLinkLocalAddress() || address.isAnyLocalAddress()
						|| address.isMulticastAddress() || isUniqueLocalV6(address)) {
					throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
				}
			}
		} catch (UnknownHostException e) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return uri;
	}

	/** IPv6 unique-local(fc00::/7) — isSiteLocalAddress 가 v6 에서는 못 잡는다. */
	private static boolean isUniqueLocalV6(InetAddress address) {
		byte[] raw = address.getAddress();
		return raw.length == 16 && (raw[0] & 0xFE) == 0xFC;
	}

	private static boolean isHttp(String url) {
		try {
			String scheme = URI.create(url.trim()).getScheme();
			return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static LinkPreviewResponse empty(String url) {
		return new LinkPreviewResponse(url, null, null, null, null);
	}

	private static String meta(Document doc, String property) {
		return attrContent(doc, "meta[property=" + property + "]");
	}

	private static String attrContent(Document doc, String selector) {
		var element = doc.selectFirst(selector);
		if (element == null) {
			return null;
		}
		String content = element.attr("content").trim();
		return content.isEmpty() ? null : content;
	}

	private static String firstNonBlank(String primary, String fallback) {
		if (primary != null && !primary.isBlank()) {
			return primary;
		}
		return fallback == null || fallback.isBlank() ? null : fallback.trim();
	}

	private static String cut(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}
}
