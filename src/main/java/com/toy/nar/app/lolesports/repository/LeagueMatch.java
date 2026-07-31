package com.toy.nar.app.lolesports.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "league_match")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LeagueMatch {

	@Id
	private String id;

	@Column(nullable = false)
	private String leagueName;

	private String matchTitle;
	private LocalDateTime matchDate;
	private String state;

	private String blueTeamCode;
	private String blueTeamName;
	private String blueExternalTeamId;
	private String blueTeamImageUrl;
	private Integer blueScore;

	private String redTeamCode;
	private String redTeamName;
	private String redExternalTeamId;
	private String redTeamImageUrl;
	private Integer redScore;

	private boolean hasVod;

	/**
	 * 세트별 승자. 콤마 구분, index = 세트 번호 (예: "B,R,B"). B/R 은 이 매치의 blue/red 기준,
	 * '?' 는 순서 미상. 업스트림이 세트별 승자를 주지 않아 스코어 전이 시점에 직접 적는다.
	 */
	@Column(length = 32)
	private String setWinners;

	@Column(columnDefinition = "TEXT")
	private String matchDetailsJson;

	private Integer seasonYear;

	@Column(length = 20)
	private String seasonSplit;

	private LocalDateTime lastUpdated;

	public void update(String leagueName, String matchTitle, LocalDateTime matchDate, String state,
					   String blueTeamCode, String blueTeamName, String blueExternalTeamId, String blueTeamImageUrl, Integer blueScore,
					   String redTeamCode, String redTeamName, String redExternalTeamId, String redTeamImageUrl, Integer redScore,
					   boolean hasVod, String matchDetailsJson, LocalDateTime lastUpdated) {
		this.leagueName = leagueName;
		this.matchTitle = matchTitle;
		this.matchDate = matchDate;
		this.state = state;
		this.blueTeamCode = blueTeamCode;
		this.blueTeamName = blueTeamName;
		this.blueExternalTeamId = blueExternalTeamId;
		this.blueTeamImageUrl = blueTeamImageUrl;
		this.blueScore = blueScore;
		this.redTeamCode = redTeamCode;
		this.redTeamName = redTeamName;
		this.redExternalTeamId = redExternalTeamId;
		this.redTeamImageUrl = redTeamImageUrl;
		this.redScore = redScore;
		this.hasVod = hasVod;
		this.matchDetailsJson = matchDetailsJson;
		this.lastUpdated = lastUpdated;
	}

	public void applySetWinners(String setWinners) {
		this.setWinners = setWinners;
	}

	public void applySeason(Integer seasonYear, String seasonSplit) {
		this.seasonYear = seasonYear;
		this.seasonSplit = seasonSplit;
	}

	public void updateExternalTeamIds(String blueExternalTeamId, String redExternalTeamId) {
		this.blueExternalTeamId = blueExternalTeamId;
		this.redExternalTeamId = redExternalTeamId;
	}
}
