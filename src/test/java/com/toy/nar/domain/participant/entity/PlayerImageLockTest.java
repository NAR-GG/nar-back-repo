package com.toy.nar.domain.participant.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 이미지 잠금 규칙: 수동 수정(overrideImage)하면 잠기고, 잠긴 뒤에는 sync 경로(setImageUrl)가 못 덮는다.
class PlayerImageLockTest {

	@Test
	@DisplayName("overrideImage는 이미지를 바꾸고 잠근다; 이후 setImageUrl(sync)은 무시된다")
	void overrideImage_locksAgainstSync() {
		Player player = Player.builder().name("Faker").imageUrl("old.png").build();

		player.overrideImage("manual.png");
		assertThat(player.getImageUrl()).isEqualTo("manual.png");
		assertThat(player.isImageLocked()).isTrue();

		player.setImageUrl("sync.png"); // 자동 동기화 경로
		assertThat(player.getImageUrl()).isEqualTo("manual.png"); // 덮어쓰기 차단
	}

	@Test
	@DisplayName("unlockImage 후에는 sync가 다시 덮어쓸 수 있다")
	void unlockImage_allowsSyncAgain() {
		Player player = Player.builder().name("Faker").imageUrl("old.png").build();
		player.overrideImage("manual.png");

		player.unlockImage();
		player.setImageUrl("sync.png");

		assertThat(player.getImageUrl()).isEqualTo("sync.png");
		assertThat(player.isImageLocked()).isFalse();
	}

	@Test
	@DisplayName("overrideGameAccounts는 계정을 바꾸고 잠근다; 이후 updateProfile(크롤러)은 gameAccounts만 보존한다")
	void overrideGameAccounts_locksAgainstCrawler() {
		Player player = Player.builder().name("Faker").build();

		player.overrideGameAccounts("[{\"region\":\"KR\",\"riotId\":\"Hide on bush#KR1\",\"tier\":null}]");
		assertThat(player.isGameAccountsLocked()).isTrue();

		player.updateProfile("이상혁", "1996-05-07", 30, "MID", "[{\"riotId\":\"stale#OLD\"}]");
		assertThat(player.getGameAccounts()).contains("Hide on bush#KR1"); // 계정은 보존
		assertThat(player.getRealName()).isEqualTo("이상혁"); // 나머지 프로필은 갱신

		player.unlockGameAccounts();
		player.updateProfile("이상혁", "1996-05-07", 30, "MID", "[{\"riotId\":\"new#SYNC\"}]");
		assertThat(player.getGameAccounts()).contains("new#SYNC");
	}
}
