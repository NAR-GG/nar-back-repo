package com.toy.nar.app.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CloudinaryUrlsTest {

	private static final String BASE = "https://res.cloudinary.com/dvvurdffw/image/upload/";

	@Test
	@DisplayName("변환이 없던 URL 에 변환을 끼운다 — 회원 프로필 591건이 이 모양")
	void insertsTransformWhenAbsent() {
		String url = BASE + "v1785198594/profiles/15.png";

		assertThat(CloudinaryUrls.with(url, CloudinaryUrls.AVATAR))
				.isEqualTo(BASE + CloudinaryUrls.AVATAR + "/v1785198594/profiles/15.png");
	}

	@Test
	@DisplayName("이미 박힌 f_auto 변환을 갈아끼운다 — 선수 11건이 이 모양")
	void replacesExistingTransform() {
		String url = BASE + "f_auto,q_auto,w_500,c_limit/v1785594927/players/18.webp";

		assertThat(CloudinaryUrls.with(url, CloudinaryUrls.PLAYER))
				.isEqualTo(BASE + CloudinaryUrls.PLAYER + "/v1785594927/players/18.webp");
	}

	@Test
	@DisplayName("버전 세그먼트를 변환으로 착각해 지우지 않는다")
	void keepsVersionSegment() {
		assertThat(CloudinaryUrls.with(BASE + "v1785198594/profiles/15.png", CloudinaryUrls.AVATAR))
				.contains("/v1785198594/profiles/15.png");
	}

	@Test
	@DisplayName("Cloudinary 가 아닌 URL 은 그대로 통과한다 — 호출부가 분기하지 않아도 되게")
	void passesThroughForeignUrls() {
		String lolesports = "https://static.lolesports.com/players/1769083583495_image6.png";
		String local = "/images/players/Deft_데프트.webp";

		assertThat(CloudinaryUrls.with(lolesports, CloudinaryUrls.PLAYER)).isEqualTo(lolesports);
		assertThat(CloudinaryUrls.with(local, CloudinaryUrls.PLAYER)).isEqualTo(local);
	}

	@Test
	@DisplayName("null 은 null 로 돌려준다 — 프로필 미설정 회원이 다수")
	void passesThroughNull() {
		assertThat(CloudinaryUrls.with(null, CloudinaryUrls.AVATAR)).isNull();
	}

	@Test
	@DisplayName("두 번 감싸도 변환이 중복되지 않는다")
	void isIdempotent() {
		String once = CloudinaryUrls.with(BASE + "v1/profiles/15.png", CloudinaryUrls.AVATAR);

		assertThat(CloudinaryUrls.with(once, CloudinaryUrls.AVATAR)).isEqualTo(once);
	}

	@Test
	@DisplayName("포맷은 f_webp 로 못박혀 있다 — f_auto 는 Flutter 에서 PNG 원본이 내려온다")
	void pinsWebpFormat() {
		assertThat(CloudinaryUrls.AVATAR).startsWith("f_webp,");
		assertThat(CloudinaryUrls.PLAYER).startsWith("f_webp,");
		assertThat(CloudinaryUrls.NOTICE).startsWith("f_webp,");
	}
}
