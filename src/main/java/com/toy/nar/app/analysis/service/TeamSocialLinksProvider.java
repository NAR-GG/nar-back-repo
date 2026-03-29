package com.toy.nar.app.analysis.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.toy.nar.app.analysis.dto.TeamSocialLinks;

@Component
public class TeamSocialLinksProvider {

	private static final Map<String, TeamSocialLinks> LINKS_BY_TEAM_CODE = Map.ofEntries(
			Map.entry("T1", new TeamSocialLinks(
					"https://www.t1.gg/",
					"https://www.instagram.com/t1lol/",
					"https://www.youtube.com/@SKTT1",
					"https://x.com/T1LoL")),
			Map.entry("HLE", new TeamSocialLinks(
					"https://hle.kr/en",
					"https://www.instagram.com/hle.official/",
					"https://www.youtube.com/@HanwhaLifeEsports",
					"https://x.com/HLEofficial")),
			Map.entry("GEN", new TeamSocialLinks(
					"https://geng.gg/",
					"https://www.instagram.com/gengesports/",
					"https://www.youtube.com/@gengesports",
					"https://x.com/GenG_KR")),
			Map.entry("DK", new TeamSocialLinks(
					"https://dpluskia.gg/",
					"https://www.instagram.com/dpluskia.lol/",
					"https://www.youtube.com/@DplusKIA_LOL",
					"https://x.com/DplusKia")),
			Map.entry("BRO", new TeamSocialLinks(
					"https://brionesports.gg/",
					"https://www.instagram.com/brionesports/",
					"https://www.youtube.com/@BRIONESPORTS",
					"https://x.com/Brionesports")),
			Map.entry("NS", new TeamSocialLinks(
					"https://ns-esports.com/",
					"https://www.instagram.com/ns_redforce/",
					"https://www.youtube.com/@NSRedForce",
					"https://x.com/NS_RedForce")),
			Map.entry("BFX", new TeamSocialLinks(
					"https://www.fearx.gg/",
					"https://www.instagram.com/bnk_fearx/",
					"https://www.youtube.com/channel/UCxedTJNaGRHiq6YfNtQVCNA",
					"https://x.com/BNKFEARXLoL")),
			Map.entry("KRX", new TeamSocialLinks(
					"https://www.drx.gg/",
					"https://www.instagram.com/drxglobal/",
					"https://www.youtube.com/drxglobal",
					"https://x.com/DRX_LCK")),
			Map.entry("KT", new TeamSocialLinks(
					"https://ktrolster.com/",
					"https://www.instagram.com/ktrolstagram/",
					"https://www.youtube.com/@ktRolster_VOD",
					"https://x.com/ktRolsterEsport")),
			Map.entry("DNS", new TeamSocialLinks(
					"https://soopers.gg/",
					"https://www.instagram.com/soopers_lol/",
					"https://www.youtube.com/@SOOPers_ESPORTS",
					"https://x.com/SOOPers_LoL")));

	public TeamSocialLinks getSocialLinks(String teamCode) {
		if (teamCode == null || teamCode.isBlank()) {
			return null;
		}
		return LINKS_BY_TEAM_CODE.get(teamCode.toUpperCase());
	}
}
