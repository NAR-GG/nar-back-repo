package com.toy.nar.app.kakao;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.toy.nar.app.lolesports.LeagueMatchService;
import com.toy.nar.app.lolesports.MatchResultDto;
import com.toy.nar.app.lolesports.MatchResultDto.TeamInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KakaoMatchThumbnailService {

	private static final String CARD_BACKGROUND = "#F5F7FB";
	private static final String CHIP_BACKGROUND = "#222938";
	private static final String CHIP_BORDER = "#3D465B";
	private static final String TEXT_COLOR = "#131722";
	private static final String SUBTEXT_COLOR = "#6E7687";

	private final LeagueMatchService leagueMatchService;
	private final RemoteImageEmbedService remoteImageEmbedService;

	@Value("${app.server.url:https://api.nar.kr}")
	private String apiServerUrl = "https://api.nar.kr";

	public String matchThumbnailUrl(String matchId) {
		return "%s/api/kakao/skills/images/matches/%s.svg".formatted(
				apiServerUrl,
				URLEncoder.encode(matchId, StandardCharsets.UTF_8));
	}

	@Cacheable(value = "kakaoMatchThumbnails", key = "#matchId")
	public String renderMatchThumbnailSvg(String matchId) {
		Optional<MatchResultDto> match = leagueMatchService.getMatchFromDbById(matchId);
		return match.map(this::buildSvg).orElseGet(() -> buildFallbackSvg("TBD", "TBD", null, null));
	}

	String buildSvg(MatchResultDto match) {
		String blueCode = teamCode(match.getBlueTeam());
		String redCode = teamCode(match.getRedTeam());
		String blueImage = resolveDataUri(match.getBlueTeam());
		String redImage = resolveDataUri(match.getRedTeam());
		return buildFallbackSvg(blueCode, redCode, blueImage, redImage);
	}

	private String resolveDataUri(TeamInfo team) {
		if (team == null || team.getImageUrl() == null || team.getImageUrl().isBlank()) {
			return null;
		}
		return remoteImageEmbedService.resolve(team.getImageUrl())
				.map(RemoteImageEmbedService.EmbeddedImage::dataUri)
				.orElse(null);
	}

	private String buildFallbackSvg(String blueCode, String redCode, String blueImage, String redImage) {
		String safeBlueCode = escapeXml(blueCode);
		String safeRedCode = escapeXml(redCode);
		StringBuilder svg = new StringBuilder();
		svg.append("""
				<svg xmlns="http://www.w3.org/2000/svg" width="720" height="400" viewBox="0 0 720 400">
				  <rect width="720" height="400" rx="36" fill="%s"/>
				  <rect x="110" y="92" width="168" height="168" rx="32" fill="%s" stroke="%s" stroke-width="4"/>
				  <rect x="442" y="92" width="168" height="168" rx="32" fill="%s" stroke="%s" stroke-width="4"/>
				  <text x="84" y="188" text-anchor="end" font-family="Arial, sans-serif" font-size="54" font-weight="700" fill="%s">%s</text>
				  <text x="636" y="188" text-anchor="start" font-family="Arial, sans-serif" font-size="54" font-weight="700" fill="%s">%s</text>
				  <text x="360" y="196" text-anchor="middle" font-family="Arial, sans-serif" font-size="84" font-weight="800" fill="%s">VS</text>
				  <text x="360" y="246" text-anchor="middle" font-family="Arial, sans-serif" font-size="26" font-weight="600" fill="%s">NAR.GG MATCH</text>
				""".formatted(CARD_BACKGROUND, CHIP_BACKGROUND, CHIP_BORDER, CHIP_BACKGROUND, CHIP_BORDER, TEXT_COLOR,
				safeBlueCode, TEXT_COLOR, safeRedCode, TEXT_COLOR, SUBTEXT_COLOR));

		appendLogo(svg, 130, 112, blueImage, safeBlueCode);
		appendLogo(svg, 462, 112, redImage, safeRedCode);
		svg.append("</svg>");
		return svg.toString();
	}

	private void appendLogo(StringBuilder svg, int x, int y, String dataUri, String fallbackText) {
		if (dataUri != null) {
			svg.append("""
					  <image href="%s" x="%d" y="%d" width="128" height="128" preserveAspectRatio="xMidYMid meet"/>
					""".formatted(dataUri, x, y));
			return;
		}

		svg.append("""
				  <text x="%d" y="%d" text-anchor="middle" font-family="Arial, sans-serif" font-size="42" font-weight="700" fill="#FFFFFF">%s</text>
				""".formatted(x + 64, y + 76, fallbackText));
	}

	private String teamCode(TeamInfo team) {
		if (team == null) {
			return "TBD";
		}
		if (team.getCode() != null && !team.getCode().isBlank()) {
			return team.getCode();
		}
		if (team.getName() != null && !team.getName().isBlank()) {
			return team.getName();
		}
		return "TBD";
	}

	private String escapeXml(String value) {
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&apos;");
	}
}
