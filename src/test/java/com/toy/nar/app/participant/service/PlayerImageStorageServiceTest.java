package com.toy.nar.app.participant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.toy.nar.app.image.CloudinaryUploadClient;
import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;

@ExtendWith(MockitoExtension.class)
class PlayerImageStorageServiceTest {

	private static final String SECURE_URL =
			"https://res.cloudinary.com/nar/image/upload/v1722500000/players/860.webp";

	@Mock
	private PlayerRepository playerRepository;

	@Mock
	private CloudinaryUploadClient uploadClient;

	@InjectMocks
	private PlayerImageStorageService service;

	private Player givenPlayer() {
		Player player = Player.builder().name("Quid").build();
		when(playerRepository.findWithCurrentTeamById(860L)).thenReturn(Optional.of(player));
		return player;
	}

	@Test
	@DisplayName("선수 ID 고정 public_id로 overwrite 업로드하고 변환 URL을 잠금과 함께 저장한다")
	void uploadsToCloudinaryAndLocksImage() {
		Player player = givenPlayer();
		when(uploadClient.upload(any(MultipartFile.class), anyString(), anyBoolean())).thenReturn(SECURE_URL);
		var file = new MockMultipartFile("file", "아무이름.webp", "image/webp", new byte[] {1, 2, 3});

		service.upload(860L, file);

		verify(uploadClient).upload(file, "players/860", true);
		// 버전(/v.../)은 Cloudinary가 붙여 주고, 전송 최적화 변환이 끼워진다.
		assertThat(player.getImageUrl())
				.isEqualTo("https://res.cloudinary.com/nar/image/upload/f_auto,q_auto,w_500,c_limit/v1722500000/players/860.webp");
		assertThat(player.isImageLocked()).isTrue();
	}

	@Test
	@DisplayName("허용하지 않는 타입은 업로드 없이 거부한다")
	void rejectsDisallowedType() {
		givenPlayer();
		var file = new MockMultipartFile("file", "x.svg", "image/svg+xml", new byte[] {1});

		assertThatThrownBy(() -> service.upload(860L, file))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("webp");
		verify(uploadClient, never()).upload(any(), anyString(), anyBoolean());
	}

	@Test
	@DisplayName("2MB를 넘으면 업로드 없이 거부한다")
	void rejectsOversizedFile() {
		givenPlayer();
		byte[] big = new byte[(int) PlayerImageStorageService.MAX_BYTES + 1];
		var file = new MockMultipartFile("file", "big.webp", "image/webp", big);

		assertThatThrownBy(() -> service.upload(860L, file))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("2MB");
		verify(uploadClient, never()).upload(any(), anyString(), anyBoolean());
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
