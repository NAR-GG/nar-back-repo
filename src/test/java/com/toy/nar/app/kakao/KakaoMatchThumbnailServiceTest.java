package com.toy.nar.app.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.MatchResultDto;

class KakaoMatchThumbnailServiceTest {

	private final LeagueMatchService leagueMatchService = Mockito.mock(LeagueMatchService.class);
	private final RemoteImageEmbedService remoteImageEmbedService = Mockito.mock(RemoteImageEmbedService.class);
	private final KakaoMatchThumbnailService service = new KakaoMatchThumbnailService(leagueMatchService, remoteImageEmbedService);

	@Test
	void svgContainsTeamCodesAndEmbeddedImages() {
		when(remoteImageEmbedService.resolve("https://img.example.com/g2.png"))
				.thenReturn(Optional.of(new RemoteImageEmbedService.EmbeddedImage("data:image/png;base64,AAA", "image/png")));
		when(remoteImageEmbedService.resolve("https://img.example.com/tsw.png"))
				.thenReturn(Optional.of(new RemoteImageEmbedService.EmbeddedImage("data:image/png;base64,BBB", "image/png")));

		String svg = service.buildSvg(match("match-1", "G2", "TSW"));

		assertThat(svg).contains("G2");
		assertThat(svg).contains("TSW");
		assertThat(svg).contains("data:image/png;base64,AAA");
		assertThat(svg).contains("#222938");
	}

	@Test
	void matchThumbnailUrlUsesApiServerUrl() {
		ReflectionTestUtils.setField(service, "apiServerUrl", "https://api.nar.kr");

		assertThat(service.matchThumbnailUrl("match-1"))
				.isEqualTo("https://api.nar.kr/api/kakao/skills/images/matches/match-1.svg");
	}

	@Test
	void renderMatchThumbnailFallsBackWhenMatchIsMissing() {
		when(leagueMatchService.getMatchFromDbById("missing")).thenReturn(Optional.empty());

		String svg = service.renderMatchThumbnailSvg("missing");

		assertThat(svg).contains("TBD");
		assertThat(svg).contains("VS");
	}

	private MatchResultDto match(String matchId, String blueCode, String redCode) {
		return MatchResultDto.builder()
				.matchId(matchId)
				.blueTeam(MatchResultDto.TeamInfo.builder().code(blueCode).imageUrl("https://img.example.com/" + blueCode.toLowerCase() + ".png").build())
				.redTeam(MatchResultDto.TeamInfo.builder().code(redCode).imageUrl("https://img.example.com/" + redCode.toLowerCase() + ".png").build())
				.build();
	}
}
