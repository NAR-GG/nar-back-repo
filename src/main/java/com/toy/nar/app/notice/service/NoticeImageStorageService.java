package com.toy.nar.app.notice.service;

import com.toy.nar.app.image.CloudinaryUploadClient;
import com.toy.nar.app.image.CloudinaryUrls;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공지 본문 이미지 저장 — Cloudinary 서버사이드 업로드.
 * 백오피스 에디터가 파일을 올리면 서버가 서명해 Cloudinary 로 넘기고 CDN URL 을 돌려준다.
 * 본문 마크다운에는 이 URL 문자열만 박히므로 서버에 상태가 남지 않는다 (볼륨 마운트 불필요).
 *
 * 전송량 절약을 위해 URL 에 딜리버리 변환({@link CloudinaryUrls#NOTICE})을 끼워 반환한다 —
 * 원본은 Cloudinary 에 그대로 있고 앱은 최적화본을 받는다.
 */
@Service
@RequiredArgsConstructor
public class NoticeImageStorageService {

    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private final CloudinaryUploadClient uploadClient;

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

        String secureUrl = uploadClient.upload(file, "notices/" + UUID.randomUUID(), false);
        return CloudinaryUrls.with(secureUrl, CloudinaryUrls.NOTICE);
    }
}
