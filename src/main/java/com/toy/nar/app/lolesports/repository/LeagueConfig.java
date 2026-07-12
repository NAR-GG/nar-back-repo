package com.toy.nar.app.lolesports.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리그별 라이브 수집/디스코드 알림/경기 동기화 토글. 백오피스에서 관리한다.
 * 키는 {@code LeagueConstants.TARGET_LEAGUES} 의 대문자 리그명.
 */
@Entity
@Table(name = "league_config")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LeagueConfig {

	@Id
	private String leagueName;

	private boolean liveEnabled;
	private boolean notificationEnabled;
	private boolean syncEnabled;

	public void update(boolean liveEnabled, boolean notificationEnabled, boolean syncEnabled) {
		this.liveEnabled = liveEnabled;
		this.notificationEnabled = notificationEnabled;
		this.syncEnabled = syncEnabled;
	}
}
