package com.toy.nar.app.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.toy.nar.config.CloudinaryProperties;

class ImageCdnTest {

	private static final String TEAM_ORIGIN = "https://static.lolesports.com/teams/1734691810721_BFX.png";
	private static final String CHAMPION_ORIGIN =
			"https://ddragon.leagueoflegends.com/cdn/16.13.1/img/champion/Aatrox.png";
	private static final String SPLASH_ORIGIN =
			"https://cdn.communitydragon.org/latest/champion/266/splash-art/centered";

	private static ImageCdn cdn(boolean enabled, String cloudName) {
		CloudinaryProperties properties = new CloudinaryProperties();
		properties.setCloudName(cloudName);
		properties.setCdnEnabled(enabled);
		return new ImageCdn(properties);
	}

	private static ImageCdn cdn() {
		return cdn(true, "nar");
	}

	@Test
	@DisplayName("허용 목록의 Riot 호스트는 fetch 로 감싼다")
	void wrapsFetchableHosts() {
		assertThat(cdn().team(TEAM_ORIGIN))
				.isEqualTo("https://res.cloudinary.com/nar/image/fetch/" + ImageCdn.TEAM + "/" + TEAM_ORIGIN);
		assertThat(cdn().champion(CHAMPION_ORIGIN))
				.isEqualTo("https://res.cloudinary.com/nar/image/fetch/" + ImageCdn.CHAMPION + "/" + CHAMPION_ORIGIN);
		assertThat(cdn().splash(SPLASH_ORIGIN))
				.isEqualTo("https://res.cloudinary.com/nar/image/fetch/" + ImageCdn.SPLASH + "/" + SPLASH_ORIGIN);
	}

	@Test
	@DisplayName("허용 목록 밖 호스트는 손대지 않는다 — 감싸면 Cloudinary 가 401 을 준다")
	void leavesForeignHostsAlone() {
		String upload = "https://res.cloudinary.com/nar/image/upload/f_webp,q_auto,w_500,c_limit/v1/players/18.webp";
		String jarPath = "/images/players/Deft_데프트.webp";
		String youtube = "https://i.ytimg.com/vi/abc/hqdefault.jpg";

		assertThat(cdn().player(upload)).isEqualTo(upload);
		assertThat(cdn().player(jarPath)).isEqualTo(jarPath);
		assertThat(cdn().player(youtube)).isEqualTo(youtube);
	}

	@Test
	@DisplayName("두 번 감싸도 결과가 같다 — 동기화 dirty 검사가 원본/래핑을 섞어 봐도 안전하게")
	void isIdempotent() {
		String once = cdn().team(TEAM_ORIGIN);

		assertThat(cdn().team(once)).isEqualTo(once);
	}

	@Test
	@DisplayName("동기화 dirty 검사가 저장값과 같게 비교된다 — 켜고도 매번 변경으로 잡히면 안 된다")
	void wrappedIncomingEqualsStored() {
		String stored = cdn().team(TEAM_ORIGIN);
		String incomingFromApi = TEAM_ORIGIN;

		assertThat(cdn().team(incomingFromApi)).isEqualTo(stored);
	}

	@Test
	@DisplayName("킬 스위치를 내리면 원본을 그대로 돌려준다")
	void passesThroughWhenDisabled() {
		assertThat(cdn(false, "nar").team(TEAM_ORIGIN)).isEqualTo(TEAM_ORIGIN);
	}

	@Test
	@DisplayName("cloud name 미설정(로컬 dev)이면 감싸지 않는다 — 감싸면 깨진 URL 이 된다")
	void passesThroughWhenCloudNameMissing() {
		assertThat(cdn(true, "").team(TEAM_ORIGIN)).isEqualTo(TEAM_ORIGIN);
		assertThat(cdn(true, null).team(TEAM_ORIGIN)).isEqualTo(TEAM_ORIGIN);
	}

	@Test
	@DisplayName("null 과 형식이 깨진 URL 은 그대로 통과한다")
	void passesThroughNullAndMalformed() {
		assertThat(cdn().team(null)).isNull();
		assertThat(cdn().team("not a url")).isEqualTo("not a url");
	}

	@Test
	@DisplayName("origin() 이 원본 URL 을 복원한다 — 언랩 SQL 과 같은 규칙")
	void unwrapsToOrigin() {
		assertThat(ImageCdn.origin(cdn().team(TEAM_ORIGIN))).isEqualTo(TEAM_ORIGIN);
		assertThat(ImageCdn.origin(cdn().splash(SPLASH_ORIGIN))).isEqualTo(SPLASH_ORIGIN);
		assertThat(ImageCdn.origin(TEAM_ORIGIN)).isEqualTo(TEAM_ORIGIN);
		assertThat(ImageCdn.origin(null)).isNull();
	}

	@Test
	@DisplayName("스플래시만 세로 크롭이다 — 원본이 1280×720 가로라 클라이언트가 잘라 쓰던 것을 서버로 옮긴다")
	void splashCropsToPortrait() {
		assertThat(ImageCdn.SPLASH).contains("w_400,h_600,c_fill,g_auto");
		assertThat(ImageCdn.TEAM).doesNotContain("c_fill");
		assertThat(ImageCdn.CHAMPION).doesNotContain("c_fill");
	}
}
