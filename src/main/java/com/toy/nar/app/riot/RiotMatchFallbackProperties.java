package com.toy.nar.app.riot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 솔랭 완료 매치(match-v5) 폴백 감지 설정.
 *
 * <p>스트리머 모드 계정은 spectator-v5에서 필터링되어 라이브 감지가 불가능하다
 * (Riot 2025-10 익명성 정책). match-v5 완료 매치 목록은 필터링되지 않으므로,
 * 경기 종료 후라도 감지·알림하기 위한 폴백 경로다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "riot.match-fallback")
public class RiotMatchFallbackProperties {
	private boolean enabled;
	private long pollIntervalMs = 300000;
	private long initialDelayMs = 45000;
	/** 계정당 조회할 최근 솔랭 매치 수. */
	private int fetchCount = 5;
	/** 이 시간(분) 내에 끝난 게임만 알림 발송. 초과분은 이력만 적재(첫 가동 백필 스팸 방지). */
	private long alertFreshnessMinutes = 60;
}
