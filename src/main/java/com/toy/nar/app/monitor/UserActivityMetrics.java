package com.toy.nar.app.monitor;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * 접속 사용자 지표를 Prometheus 로 내보낸다. WhaTap 의 "동시접속 사용자"·"금일 사용자"
 * 자리를 대신한다(그쪽은 이번 달 해지 예정).
 *
 * Gauge 는 스크랩할 때마다 함수를 호출하므로 별도 스케줄러가 필요 없다. 그 김에
 * 오래된 항목 정리도 여기서 같이 한다 — 안 하면 5분 지난 항목이 맵에 계속 쌓인다.
 */
@Component
@RequiredArgsConstructor
public class UserActivityMetrics {

	private final MeterRegistry meterRegistry;
	private final UserActivityService userActivityService;

	@PostConstruct
	void register() {
		Gauge.builder("nar.active.users", userActivityService, s -> {
				s.cleanupOldUsers();
				return s.getActiveUsersCount();
			})
			.description("최근 5분 내 요청한 고유 사용자 수 (로그인 회원 + 비회원 IP)")
			.register(meterRegistry);

		Gauge.builder("nar.daily.users", userActivityService, UserActivityService::getDailyUsersCount)
			.description("오늘(KST) 요청한 고유 사용자 수. 자정에 리셋된다")
			.register(meterRegistry);
	}
}
