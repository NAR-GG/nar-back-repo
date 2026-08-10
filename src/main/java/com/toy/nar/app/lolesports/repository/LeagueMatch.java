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
	/** 다전제 규격(1/3/5). 업스트림 match.strategy.count. 과거 데이터는 완료 스코어로 역산됐다. */
	private Integer bestOf;

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

	/** 스코어만 반영한다 — state 를 옮기면 되돌림 클래스(#354/#355)가 재발하므로 건드리지 않는다. */
	public void applyScore(Integer blueScore, Integer redScore, LocalDateTime lastUpdated) {
		this.blueScore = blueScore;
		this.redScore = redScore;
		this.lastUpdated = lastUpdated;
	}

	/** 업스트림이 준 다전제 규격을 반영한다. null 이면(업스트림 공백) 기존 값을 유지한다. */
	public void applyBestOf(Integer bestOf) {
		if (bestOf != null) {
			this.bestOf = bestOf;
		}
	}

	/**
	 * 이미 확정한 경기 결과(상태·스코어)를 그대로 옮겨 담는다.
	 *
	 * <p>업스트림이 끝난 경기를 미시작·0:0 으로 방치할 때, 그 값으로 덮이지 않도록
	 * 동기화 대상 엔티티에 기존 결과를 되돌려 놓는 용도다.</p>
	 */
	public void restoreResult(String state, Integer blueScore, Integer redScore) {
		this.state = state;
		this.blueScore = blueScore;
		this.redScore = redScore;
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
