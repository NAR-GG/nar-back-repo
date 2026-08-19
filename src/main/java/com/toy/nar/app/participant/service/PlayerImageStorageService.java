package com.toy.nar.app.participant.service;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.toy.nar.app.image.CloudinaryUploadClient;
import com.toy.nar.app.image.CloudinaryUrls;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;

import lombok.RequiredArgsConstructor;

/**
 * 백오피스 선수 이미지 업로드 — Cloudinary 서버사이드 업로드 (공지·프로필 이미지와 동일 저장소).
 *
 * <p>배포 없이 이미지를 교체할 수 있게 하는 것이 목적이다. public_id 를 선수 ID 로 고정하고
 * overwrite 로 덮어쓰므로 고아 자산이 쌓이지 않고, 반환 URL 에 버전(/v{ts}/)이 박혀
 * 재업로드 시 캐시 무효화가 따로 필요 없다. 서버에 상태가 없어 볼륨·백업 걱정도 없다.
 *
 * <p>{@code PUT /players/{id}}(LCK 출전 이력 필수)와 달리 리그 게이트가 없다. LCK CL·LCS 선수도
 * 이미지가 필요하고, 부착 API의 imageUrl 경로는 {@code setImageUrl}이라 잠금이 걸리지 않았다.
 * 여기서는 {@code overrideImage}로 저장해 자동 동기화가 덮어쓰지 못하게 잠근다.
 */
@Service
@RequiredArgsConstructor
public class PlayerImageStorageService {

	static final long MAX_BYTES = 2 * 1024 * 1024;

	// 포맷은 업로드된 파일명을 믿지 않고 Content-Type으로 검증한다(위장 확장자 차단).
	private static final Map<String, String> ALLOWED_TYPES = Map.of(
			"image/webp", "webp",
			"image/png", "png",
			"image/jpeg", "jpg");

	private final PlayerRepository playerRepository;
	private final CloudinaryUploadClient uploadClient;

	@Transactional
	public Player upload(Long playerId, MultipartFile file) {
		Player player = playerRepository.findWithCurrentTeamById(playerId)
				.orElseThrow(() -> new NoSuchElementException("선수를 찾을 수 없습니다: " + playerId));
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("이미지 파일이 비어 있습니다");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new IllegalArgumentException("이미지는 2MB 이하여야 합니다: " + file.getSize() + "바이트");
		}
		if (!ALLOWED_TYPES.containsKey(file.getContentType())) {
			throw new IllegalArgumentException("webp·png·jpeg만 올릴 수 있습니다: " + file.getContentType());
		}

		String secureUrl = uploadClient.upload(file, "players/" + playerId, true);
		player.overrideImage(CloudinaryUrls.with(secureUrl, CloudinaryUrls.PLAYER));
		return player;
	}
}
