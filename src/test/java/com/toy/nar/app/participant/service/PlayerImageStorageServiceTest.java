package com.toy.nar.app.participant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.toy.nar.config.PlayerImageProperties;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;

@ExtendWith(MockitoExtension.class)
class PlayerImageStorageServiceTest {

	@Mock
	private PlayerRepository playerRepository;

	@TempDir
	Path tempDir;

	private PlayerImageStorageService service;

	@BeforeEach
	void setUp() {
		PlayerImageProperties properties = new PlayerImageProperties();
		properties.setDir(tempDir.toString());
		service = new PlayerImageStorageService(playerRepository, properties);
	}

	private Player givenPlayer() {
		Player player = Player.builder().name("Quid").build();
		when(playerRepository.findWithCurrentTeamById(860L)).thenReturn(Optional.of(player));
		return player;
	}

	@Test
	@DisplayName("webp를 올리면 players/{id}.webp 로 저장하고 image_url을 잠금과 함께 갱신한다")
	void savesFileAndLocksImage() throws Exception {
		Player player = givenPlayer();
		var file = new MockMultipartFile("file", "아무이름.webp", "image/webp", new byte[] {1, 2, 3});

		service.upload(860L, file);

		Path saved = tempDir.resolve("players/860.webp");
		assertThat(Files.exists(saved)).isTrue();
		assertThat(Files.readAllBytes(saved)).containsExactly(1, 2, 3);
		// 캐시 무효화용 ?v= 가 붙는다 — 같은 파일명을 덮어써도 앱이 새 이미지를 받는다.
		assertThat(player.getImageUrl()).startsWith("/images/players/860.webp?v=");
		assertThat(player.isImageLocked()).isTrue();
	}

	@Test
	@DisplayName("재업로드는 같은 파일명을 덮어쓴다(고아 파일 없음)")
	void overwritesOnReupload() throws Exception {
		Player player = Player.builder().name("Quid").build();
		when(playerRepository.findWithCurrentTeamById(860L)).thenReturn(Optional.of(player));

		service.upload(860L, new MockMultipartFile("file", "a.webp", "image/webp", new byte[] {1}));
		service.upload(860L, new MockMultipartFile("file", "b.webp", "image/webp", new byte[] {9, 9}));

		try (var entries = Files.list(tempDir.resolve("players"))) {
			assertThat(entries).hasSize(1);
		}
		assertThat(Files.readAllBytes(tempDir.resolve("players/860.webp"))).containsExactly(9, 9);
	}

	@Test
	@DisplayName("확장자는 파일명이 아니라 Content-Type으로 정한다")
	void picksExtensionFromContentType() {
		givenPlayer();

		service.upload(860L, new MockMultipartFile("file", "../../evil.webp", "image/png", new byte[] {1}));

		assertThat(Files.exists(tempDir.resolve("players/860.png"))).isTrue();
	}

	@Test
	@DisplayName("허용하지 않는 타입은 거부한다")
	void rejectsDisallowedType() {
		givenPlayer();
		var file = new MockMultipartFile("file", "x.svg", "image/svg+xml", new byte[] {1});

		assertThatThrownBy(() -> service.upload(860L, file))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("webp");
	}

	@Test
	@DisplayName("2MB를 넘으면 거부한다")
	void rejectsOversizedFile() {
		givenPlayer();
		byte[] big = new byte[(int) PlayerImageStorageService.MAX_BYTES + 1];
		var file = new MockMultipartFile("file", "big.webp", "image/webp", big);

		assertThatThrownBy(() -> service.upload(860L, file))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("2MB");
	}

	@Test
	@DisplayName("빈 파일은 거부한다")
	void rejectsEmptyFile() {
		givenPlayer();
		var file = new MockMultipartFile("file", "empty.webp", "image/webp", new byte[0]);

		assertThatThrownBy(() -> service.upload(860L, file))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("없는 선수면 NoSuchElementException")
	void rejectsUnknownPlayer() {
		when(playerRepository.findWithCurrentTeamById(999L)).thenReturn(Optional.empty());
		var file = new MockMultipartFile("file", "x.webp", "image/webp", new byte[] {1});

		assertThatThrownBy(() -> service.upload(999L, file))
				.isInstanceOf(NoSuchElementException.class);
	}
}
