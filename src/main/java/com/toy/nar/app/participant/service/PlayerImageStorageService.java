package com.toy.nar.app.participant.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.toy.nar.config.PlayerImageProperties;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;

import lombok.RequiredArgsConstructor;

/**
 * 백오피스 선수 이미지 업로드. 파일은 컨테이너 밖 디렉토리에 쓰고 DB엔 상대경로만 남긴다.
 *
 * <p>배포 없이 이미지를 교체할 수 있게 하는 것이 목적이다. 기존 경로 형식(/images/players/…)을
 * 유지하므로 앱·백오피스의 이미지 표시 로직은 손댈 필요가 없다.
 *
 * <p>{@code PUT /players/{id}}(LCK 출전 이력 필수)와 달리 리그 게이트가 없다. LCK CL·LCS 선수도
 * 이미지가 필요하고, 부착 API의 imageUrl 경로는 {@code setImageUrl}이라 잠금이 걸리지 않았다.
 * 여기서는 {@code overrideImage}로 저장해 자동 동기화가 덮어쓰지 못하게 잠근다.
 */
@Service
@RequiredArgsConstructor
public class PlayerImageStorageService {

	static final long MAX_BYTES = 2 * 1024 * 1024;

	// 확장자는 업로드된 파일명을 믿지 않고 Content-Type에서 정한다(경로 조작·위장 확장자 차단).
	private static final Map<String, String> ALLOWED_TYPES = Map.of(
			"image/webp", "webp",
			"image/png", "png",
			"image/jpeg", "jpg");

	private final PlayerRepository playerRepository;
	private final PlayerImageProperties properties;

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
		String extension = ALLOWED_TYPES.get(file.getContentType());
		if (extension == null) {
			throw new IllegalArgumentException("webp·png·jpeg만 올릴 수 있습니다: " + file.getContentType());
		}

		// 파일명은 선수 ID로 고정 — 재업로드 시 덮어쓰므로 고아 파일이 쌓이지 않는다.
		String fileName = playerId + "." + extension;
		Path directory = Path.of(properties.getDir(), "players");
		try {
			Files.createDirectories(directory);
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new IllegalStateException("이미지 저장 실패: " + fileName, e);
		}

		// 같은 파일명을 덮어쓰므로 캐시 무효화용 버전 쿼리를 붙인다(앱·브라우저가 옛 이미지를 계속 쓰는 것 방지).
		player.overrideImage("/images/players/" + fileName + "?v=" + Instant.now().getEpochSecond());
		return player;
	}
}
