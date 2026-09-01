package com.toy.nar.app.store;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.auth.oauth2.ServiceAccountCredentials;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Google Play Developer API 클라이언트 — 리뷰 조회와 프로덕션 트랙 조회.
 *
 * <p>플레이에는 웹훅이 없다. RTDN(Pub/Sub)은 결제·구독 전용이라 리뷰도 출시도 폴링뿐이다.
 * 애플과 달리 <b>심사 중·거부 상태는 API 로 아예 안 나온다</b> — 구글은 이메일만 보낸다.
 * 그래서 여기서 잡는 배포 신호는 "프로덕션 트랙에 올라간 버전과 그 롤아웃 상태" 하나다.
 *
 * <p>ponytail: {@code google-api-services-androidpublisher} 를 붙이지 않는다. GET 두 개라
 * 이미 있는 {@code google-auth-library}(토큰 발급·갱신) + WebClient 로 끝난다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayConsoleClient {

	private static final String BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/";
	private static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";

	private final WebClient webClient;

	/** Play Console 에 연결한 GCP 서비스 계정 키 JSON 원문. 드라이브용 키와 다른 키다. */
	@Value("${play-store.service-account-key:}")
	private String serviceAccountKeyJson;

	@Value("${play-store.package-name:}")
	private String packageName;

	private volatile ServiceAccountCredentials credentials;

	public boolean isAvailable() {
		return !isBlank(serviceAccountKeyJson) && !isBlank(packageName);
	}

	/**
	 * 리뷰 목록. <b>구글은 최근 7일치만 준다</b> — 그보다 오래된 리뷰는 API 로 못 가져오고
	 * Play Console CSV 뿐이다. 폴링 주기를 7일보다 짧게 유지하는 게 유실 방지 조건이다.
	 *
	 * <p>실패하면 빈 목록 — 다음 폴링이 다시 시도한다.
	 */
	public List<PlayReview> fetchRecentReviews(int limit) {
		JsonNode response = get("reviews?maxResults=" + limit, "리뷰");
		if (response == null) {
			return List.of();
		}

		List<PlayReview> reviews = new ArrayList<>();
		for (JsonNode review : response.path("reviews")) {
			// comments 는 사용자 댓글과 개발자 답글이 섞인 배열이다. 첫 userComment 만 본다.
			for (JsonNode comment : review.path("comments")) {
				JsonNode user = comment.path("userComment");
				if (user.isMissingNode()) {
					continue;
				}
				reviews.add(new PlayReview(
						review.path("reviewId").asText(),
						user.path("starRating").asInt(0),
						user.path("text").asText(""),
						review.path("authorName").asText(""),
						user.path("reviewerLanguage").asText(""),
						user.path("device").asText(""),
						user.path("appVersionName").asText(""),
						// 플레이는 epoch 초로 준다.
						user.path("lastModified").path("seconds").asLong(0)));
				break;
			}
		}
		return reviews;
	}

	/**
	 * 프로덕션 트랙의 릴리스 목록.
	 *
	 * <p>트랙 조회는 edit 안에서만 된다 — {@code edits.insert} 로 트랜잭션을 열고 읽은 뒤
	 * 버린다. 커밋하지 않으므로 스토어에 아무 변화도 만들지 않는다. 다만 <b>서비스 계정에
	 * 출시 관련 권한이 필요하다</b>(조회 전용 경로가 없다). 권한이 부족하면 403 이 뜬다.
	 */
	public List<PlayRelease> fetchProductionReleases() {
		String editId = createEdit();
		if (editId == null) {
			return List.of();
		}
		try {
			JsonNode response = get("edits/" + editId + "/tracks/production", "프로덕션 트랙");
			if (response == null) {
				return List.of();
			}
			List<PlayRelease> releases = new ArrayList<>();
			for (JsonNode release : response.path("releases")) {
				List<String> versionCodes = new ArrayList<>();
				for (JsonNode code : release.path("versionCodes")) {
					versionCodes.add(code.asText());
				}
				releases.add(new PlayRelease(
						release.path("name").asText(""),
						release.path("status").asText(""),
						// completed 면 userFraction 이 없다(100%).
						release.path("userFraction").isMissingNode() ? 1.0 : release.path("userFraction").asDouble(),
						versionCodes));
			}
			return releases;
		} finally {
			deleteEdit(editId);
		}
	}

	private String createEdit() {
		try {
			JsonNode created = webClient.post()
					.uri(BASE + packageName + "/edits")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
			return created == null ? null : created.path("id").asText(null);
		} catch (Exception e) {
			log.warn("플레이 edit 생성 실패(서비스 계정 출시 권한을 확인한다): {}", e.getMessage());
			return null;
		}
	}

	/** 열어 둔 edit 는 반드시 지운다. 남겨도 스토어에 영향은 없지만 콘솔에 쓰레기가 쌓인다. */
	private void deleteEdit(String editId) {
		try {
			webClient.delete()
					.uri(BASE + packageName + "/edits/" + editId)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
					.retrieve()
					.toBodilessEntity()
					.block();
		} catch (Exception e) {
			log.warn("플레이 edit 삭제 실패 (editId={}): {}", editId, e.getMessage());
		}
	}

	private JsonNode get(String path, String what) {
		try {
			return webClient.get()
					.uri(BASE + packageName + "/" + path)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block();
		} catch (Exception e) {
			log.warn("플레이 {} 조회 실패: {}", what, e.getMessage());
			return null;
		}
	}

	/** 토큰 발급·갱신은 google-auth-library 가 한다. 만료 임박이면 알아서 새로 받는다. */
	private String accessToken() throws java.io.IOException {
		ServiceAccountCredentials cached = credentials;
		if (cached == null) {
			// JSON 원문을 그대로 넘긴다. GoogleDriveConfig 처럼 \n 을 치환하면 안 된다 —
			// private_key 안의 \n 은 JSON 이스케이프라서 실제 개행으로 바꾸면 JSON 이 깨진다.
			// 시크릿은 --from-file 로 파일 바이트 그대로 봉인하므로 이스케이프가 온전하다.
			cached = (ServiceAccountCredentials) ServiceAccountCredentials
					.fromStream(new ByteArrayInputStream(
							serviceAccountKeyJson.getBytes(StandardCharsets.UTF_8)))
					.createScoped(List.of(SCOPE));
			credentials = cached;
		}
		cached.refreshIfExpired();
		return cached.getAccessToken().getTokenValue();
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	/** 플레이 리뷰 한 건. */
	public record PlayReview(
			String id,
			int rating,
			String text,
			String authorName,
			String language,
			String device,
			String appVersion,
			long lastModifiedEpochSeconds) {
	}

	/** 프로덕션 트랙 릴리스 한 건. {@code status} 는 draft|inProgress|halted|completed. */
	public record PlayRelease(
			String name,
			String status,
			double userFraction,
			List<String> versionCodes) {

		/** 알림 dedupe 키. 트랙에 여러 버전이 실릴 수 있어 쉼표로 묶는다. */
		public String versionKey() {
			return String.join(",", versionCodes);
		}
	}
}
