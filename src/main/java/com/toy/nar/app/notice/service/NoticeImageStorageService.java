package com.toy.nar.app.notice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.auth.profile.CloudinarySignatureService;
import com.toy.nar.config.CloudinaryProperties;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 공지 본문 이미지 저장 — Cloudinary 서버사이드 업로드.
 * 백오피스 에디터가 파일을 올리면 서버가 서명해 Cloudinary 로 넘기고 CDN URL 을 돌려준다.
 * 본문 마크다운에는 이 URL 문자열만 박히므로 서버에 상태가 남지 않는다 (볼륨 마운트 불필요).
 *
 * 전송량 절약을 위해 URL 에 f_auto,q_auto,w_750 변환을 끼워 반환한다 —
 * 원본은 Cloudinary 에 그대로 있고 앱은 최적화본을 받는다.
 */
@Service
@RequiredArgsConstructor
public class NoticeImageStorageService {

    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    /** 폰 화면(시안 375, @3x=1125)에 충분한 폭 + 포맷/품질 자동. */
    private static final String DELIVERY_TRANSFORM = "f_auto,q_auto,w_750,c_limit";

    private final CloudinaryProperties properties;
    private final CloudinarySignatureService signatureService;
    private final WebClient webClient;

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("이미지는 5MB 이하만 업로드할 수 있습니다.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다. (png/jpg/webp/gif)");
        }

        String publicId = "notices/" + UUID.randomUUID();
        long timestamp = Instant.now().getEpochSecond();
        String signature = signatureService.sign(Map.of(
                "public_id", publicId,
                "timestamp", String.valueOf(timestamp)));

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", toResource(file));
        body.part("public_id", publicId);
        body.part("timestamp", String.valueOf(timestamp));
        body.part("api_key", properties.getApiKey());
        body.part("signature", signature);

        JsonNode response = webClient.post()
                .uri("https://api.cloudinary.com/v1_1/" + properties.getCloudName() + "/image/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (response == null || response.path("secure_url").isMissingNode()) {
            throw new IllegalStateException("이미지 업로드에 실패했습니다.");
        }
        return withDeliveryTransform(response.path("secure_url").asText());
    }

    private static ByteArrayResource toResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String name = file.getOriginalFilename();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return name == null || name.isBlank() ? "image" : name;
                }
            };
        } catch (java.io.IOException e) {
            throw new IllegalStateException("업로드 파일을 읽지 못했습니다.", e);
        }
    }

    /** https://res.cloudinary.com/{cloud}/image/upload/... → /upload/{transform}/... */
    private static String withDeliveryTransform(String secureUrl) {
        return secureUrl.replaceFirst("/image/upload/", "/image/upload/" + DELIVERY_TRANSFORM + "/");
    }
}
