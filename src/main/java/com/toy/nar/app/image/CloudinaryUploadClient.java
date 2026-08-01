package com.toy.nar.app.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.toy.nar.app.auth.profile.CloudinarySignatureService;
import com.toy.nar.config.CloudinaryProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Cloudinary 서버사이드 업로드 공용 클라이언트. 서버가 서명해 넘기고 CDN URL(secure_url)을 돌려준다.
 * URL 에 버전(/v{ts}/)이 박혀 오므로 같은 public_id 를 덮어써도 캐시 무효화가 따로 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class CloudinaryUploadClient {

    private final CloudinaryProperties properties;
    private final CloudinarySignatureService signatureService;
    private final WebClient webClient;

    /** 업로드 후 secure_url 반환. overwrite=true 면 같은 public_id 를 교체한다(고아 자산 없음). */
    public String upload(MultipartFile file, String publicId, boolean overwrite) {
        long timestamp = Instant.now().getEpochSecond();
        Map<String, String> params = new HashMap<>();
        params.put("public_id", publicId);
        params.put("timestamp", String.valueOf(timestamp));
        if (overwrite) {
            params.put("overwrite", "true");
        }
        String signature = signatureService.sign(params);

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", toResource(file));
        params.forEach(body::part);
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
        return response.path("secure_url").asText();
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
}
