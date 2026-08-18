package com.toy.nar.app.riot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 솔랭 종료 알림 설정.
 *
 * <p>기본 OFF 다. 앱의 시작/종료 토글 UI 가 배포되기 전에는 사용자가 끌 방법이 없으므로
 * 서버에서 통째로 막아 둔다. 사용자 토글({@code member_favorite_player.end_enabled}, 기본 OFF)과
 * 별개의 전역 스위치다 — 이게 꺼져 있으면 감지도 Riot 조회도 발송도 전부 하지 않는다.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "solo-rank.end-notification")
public class SoloRankEndNotificationProperties {
	private boolean enabled;
	/** 결과 대기 중인 게임을 다시 확인하는 주기. match-v5 발행 지연이 실측 전이라 초안값이다. */
	private long sweepIntervalMs = 30000;
	private long initialDelayMs = 60000;
}
