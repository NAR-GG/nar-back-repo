package com.toy.nar.app.community.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toy.nar.app.auth.profile.CloudinarySignatureService;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

/**
 * 블록 본문(bodyFormat=BLOCKS) 검증·정규화. 본문의 진실은 블록 JSON 배열이다:
 *
 * <pre>
 * [{"type":"text","text":"...","style":"body|heading"},
 *  {"type":"image","url":"https://res.cloudinary.com/..."},
 *  {"type":"link","url":"...","title":"...","description":"...","imageUrl":"..."},
 *  {"type":"embed","provider":"youtube|chzzk|soop|x","url":"..."}]
 * </pre>
 *
 * <p>검증만 하고 원문을 저장하지 않는다 — 알려진 필드만 남긴 <b>정규화 JSON 을
 * 다시 직렬화</b>해서 돌려준다. 클라이언트가 임의 필드를 끼워 넣어도 DB 에
 * 남지 않고, 렌더러(앱)는 계약된 필드만 만난다.</p>
 *
 * <p>image url 은 글 첨부와 같은 규칙(우리 Cloudinary 만). embed 는 제공자별
 * 도메인 화이트리스트, link 는 http(s)면 된다 — 링크 카드는 임의 사이트가
 * 목적이고, 크롤링은 서버 link-preview API 가 SSRF 가드 아래에서만 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class CommunityBlockValidator {

	static final int MAX_BLOCKS = 50;
	static final int MAX_TOTAL_TEXT = 10_000;
	static final int MAX_IMAGES = 5;
	static final int MAX_URL_LENGTH = 500;
	static final int MAX_LINK_TITLE = 200;
	static final int MAX_LINK_DESCRIPTION = 300;
	static final int PREVIEW_LENGTH = 150;

	/** 임베드 제공자 → 허용 호스트(서브도메인 포함 suffix 매칭). */
	private static final Map<String, Set<String>> EMBED_HOSTS = Map.of(
			"youtube", Set.of("youtube.com", "youtu.be"),
			"chzzk", Set.of("chzzk.naver.com"),
			"soop", Set.of("sooplive.co.kr", "afreecatv.com"),
			"x", Set.of("x.com", "twitter.com"));

	private final ObjectMapper objectMapper;
	private final CloudinarySignatureService cloudinarySignatureService;

	/** 검증 결과 — 정규화된 본문 JSON, 목록 미리보기, 블록 순서대로의 이미지 URL. */
	public record ParsedBlocks(String normalizedBody, String preview, List<String> imageUrls) {
	}

	public ParsedBlocks validate(String rawBody) {
		JsonNode root = parse(rawBody);
		if (!root.isArray() || root.isEmpty() || root.size() > MAX_BLOCKS) {
			throw invalid();
		}
		ArrayNode normalized = objectMapper.createArrayNode();
		List<String> imageUrls = new ArrayList<>();
		StringBuilder allText = new StringBuilder();

		for (JsonNode block : root) {
			if (!block.isObject()) {
				throw invalid();
			}
			String type = text(block, "type");
			switch (type == null ? "" : type) {
				case "text" -> normalized.add(normalizeText(block, allText));
				case "image" -> normalized.add(normalizeImage(block, imageUrls));
				case "link" -> normalized.add(normalizeLink(block));
				case "embed" -> normalized.add(normalizeEmbed(block));
				default -> throw invalid();
			}
		}
		if (allText.length() > MAX_TOTAL_TEXT) {
			throw invalid();
		}
		if (imageUrls.size() > MAX_IMAGES) {
			throw invalid();
		}
		return new ParsedBlocks(write(normalized), preview(root), List.copyOf(imageUrls));
	}

	private ObjectNode normalizeText(JsonNode block, StringBuilder allText) {
		String textValue = block.path("text").isTextual() ? block.path("text").asText() : null;
		if (textValue == null) {
			throw invalid();
		}
		allText.append(textValue);
		String style = text(block, "style");
		if (style != null && !style.equals("body") && !style.equals("heading")) {
			throw invalid();
		}
		ObjectNode node = objectMapper.createObjectNode();
		node.put("type", "text");
		node.put("text", textValue);
		node.put("style", style == null ? "body" : style);
		return node;
	}

	private ObjectNode normalizeImage(JsonNode block, List<String> imageUrls) {
		String url = text(block, "url");
		if (url == null || url.length() > MAX_URL_LENGTH || !cloudinarySignatureService.isOurSecureUrl(url)) {
			throw invalid();
		}
		imageUrls.add(url);
		ObjectNode node = objectMapper.createObjectNode();
		node.put("type", "image");
		node.put("url", url);
		return node;
	}

	private ObjectNode normalizeLink(JsonNode block) {
		String url = requireHttpUrl(text(block, "url"));
		ObjectNode node = objectMapper.createObjectNode();
		node.put("type", "link");
		node.put("url", url);
		putTrimmed(node, "title", text(block, "title"), MAX_LINK_TITLE);
		putTrimmed(node, "description", text(block, "description"), MAX_LINK_DESCRIPTION);
		String imageUrl = text(block, "imageUrl");
		if (imageUrl != null && imageUrl.length() <= MAX_URL_LENGTH && isHttpUrl(imageUrl)) {
			node.put("imageUrl", imageUrl);
		}
		putTrimmed(node, "siteName", text(block, "siteName"), MAX_LINK_TITLE);
		return node;
	}

	private ObjectNode normalizeEmbed(JsonNode block) {
		String provider = text(block, "provider");
		Set<String> hosts = provider == null ? null : EMBED_HOSTS.get(provider);
		if (hosts == null) {
			throw invalid();
		}
		String url = requireHttpUrl(text(block, "url"));
		String host = URI.create(url).getHost();
		if (host == null) {
			throw invalid();
		}
		String lowered = host.toLowerCase(Locale.ROOT);
		boolean allowed = hosts.stream()
				.anyMatch(h -> lowered.equals(h) || lowered.endsWith("." + h));
		if (!allowed) {
			throw invalid();
		}
		ObjectNode node = objectMapper.createObjectNode();
		node.put("type", "embed");
		node.put("provider", provider);
		node.put("url", url);
		return node;
	}

	/** 목록 미리보기 = 첫 번째로 내용 있는 text 블록의 앞 150자. 텍스트가 없으면 null. */
	private static String preview(JsonNode root) {
		for (JsonNode block : root) {
			if ("text".equals(block.path("type").asText()) && block.path("text").isTextual()) {
				String trimmed = block.path("text").asText().trim();
				if (!trimmed.isEmpty()) {
					return trimmed.length() <= PREVIEW_LENGTH ? trimmed : trimmed.substring(0, PREVIEW_LENGTH);
				}
			}
		}
		return null;
	}

	private static String requireHttpUrl(String url) {
		if (url == null || url.length() > MAX_URL_LENGTH || !isHttpUrl(url)) {
			throw invalid();
		}
		return url;
	}

	private static boolean isHttpUrl(String url) {
		try {
			URI uri = URI.create(url);
			String scheme = uri.getScheme();
			return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static void putTrimmed(ObjectNode node, String field, String value, int max) {
		if (value == null || value.isBlank()) {
			return;
		}
		String trimmed = value.trim();
		node.put(field, trimmed.length() <= max ? trimmed : trimmed.substring(0, max));
	}

	private static String text(JsonNode block, String field) {
		JsonNode node = block.path(field);
		return node.isTextual() ? node.asText() : null;
	}

	private JsonNode parse(String rawBody) {
		try {
			return objectMapper.readTree(rawBody);
		} catch (JsonProcessingException e) {
			throw invalid();
		}
	}

	private String write(ArrayNode normalized) {
		try {
			return objectMapper.writeValueAsString(normalized);
		} catch (JsonProcessingException e) {
			throw invalid();
		}
	}

	private static CustomException invalid() {
		return new CustomException(ErrorCode.INVALID_INPUT_VALUE);
	}
}
