package com.toy.nar.app.auth.profile;

import com.toy.nar.app.auth.profile.dto.ProfileImageUploadSignatureResponse;
import com.toy.nar.config.CloudinaryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Cloudinary 서명 업로드용 서명 생성기.
 * 앱(폰)이 직접 Cloudinary로 업로드하되, 서명은 서버(시크릿 보관)에서만 만들어
 * 로그인한 사용자만 업로드할 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
public class CloudinarySignatureService {

	private final CloudinaryProperties properties;

	/**
	 * Cloudinary 규칙대로 서명한다: 파라미터를 키 알파벳순으로 정렬해
	 * {@code k=v&k2=v2} 로 이은 뒤 api_secret을 덧붙여 SHA-1 hex.
	 */
	public String sign(Map<String, String> paramsToSign) {
		String toSign = new TreeMap<>(paramsToSign).entrySet().stream()
				.filter(e -> e.getValue() != null && !e.getValue().isEmpty())
				.map(e -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining("&"));
		return sha1Hex(toSign + properties.getApiSecret());
	}

	/** 회원별 프로필 이미지 업로드 서명 파라미터를 만든다. public_id는 회원당 고정(덮어쓰기). */
	public ProfileImageUploadSignatureResponse buildProfileUpload(Long memberId, long timestamp) {
		String publicId = "profiles/" + memberId;
		Map<String, String> params = Map.of(
				"overwrite", "true",
				"public_id", publicId,
				"timestamp", String.valueOf(timestamp));
		String signature = sign(params);
		String uploadUrl = "https://api.cloudinary.com/v1_1/" + properties.getCloudName() + "/image/upload";
		return new ProfileImageUploadSignatureResponse(
				properties.getCloudName(),
				properties.getApiKey(),
				timestamp,
				publicId,
				true,
				signature,
				uploadUrl);
	}

	/**
	 * 커뮤니티 첨부 사진 업로드 서명. 프로필과 달리 이미지마다 새 public_id(UUID) —
	 * 덮어쓰기가 없어야 글에 이미 붙은 사진이 나중 업로드로 바뀌지 않는다.
	 */
	public ProfileImageUploadSignatureResponse buildCommunityUpload(Long memberId, long timestamp) {
		String publicId = "community/" + memberId + "/" + java.util.UUID.randomUUID();
		Map<String, String> params = Map.of(
				"public_id", publicId,
				"timestamp", String.valueOf(timestamp));
		String signature = sign(params);
		String uploadUrl = "https://api.cloudinary.com/v1_1/" + properties.getCloudName() + "/image/upload";
		return new ProfileImageUploadSignatureResponse(
				properties.getCloudName(),
				properties.getApiKey(),
				timestamp,
				publicId,
				false,
				signature,
				uploadUrl);
	}

	/** 앱이 보낸 secure_url 이 우리 Cloudinary 것인지. 외부 URL 주입(핫링크·우회 첨부)을 막는다. */
	public boolean isOurSecureUrl(String url) {
		return url != null && url.startsWith("https://res.cloudinary.com/" + properties.getCloudName() + "/");
	}

	private static String sha1Hex(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-1 미지원", e);
		}
	}
}
