package com.toy.nar.app.lolesports;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MatchResultDto {
	private String matchTitle;      // T1 vs GEN
	private String matchDate;       // 경기 시간
	private String score;           // 3 : 1
	private TeamInfo blueTeam;
	private TeamInfo redTeam;
	private List<SetVod> sets;      // 세트별 VOD 리스트

	@Data
	@Builder
	public static class TeamInfo {
		private String code;        // T1
		private String name;        // T1
		private int wins;           // 승리 횟수
	}

	@Data
	@Builder
	public static class SetVod {
		private int setNumber;      // 1세트, 2세트...
		private String vodUrl;      // 한국어 유튜브 링크 (시간 포함)
	}
}
