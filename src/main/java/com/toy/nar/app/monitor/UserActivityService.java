package com.toy.nar.app.monitor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 접속 사용자 집계. WhaTap 의 "동시접속 사용자"·"금일 사용자"를 대체한다.
 *
 * 식별자는 로그인 회원이면 {@code m:{memberId}}, 아니면 {@code i:{clientIp}} 다
 * (UserActivityFilter 참고). 회원이 여러 기기로 붙으면 한 명으로 센다 — WhaTap 과
 * 같은 정의다.
 *
 * <p><b>이 값은 파드 하나의 것이다.</b> 웹 파드를 2대 이상으로 늘리면 각자 자기
 * 집계만 갖는다. Prometheus 는 instance 라벨로 나눠 받으니 대시보드에서
 * {@code sum} 하면 되지만, 같은 사용자가 두 파드에 걸치면 중복으로 센다.
 * 정확한 합산이 필요해지면 그때 Redis 같은 공유 저장소로 옮긴다.
 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

	private static final long ACTIVE_DURATION_MS = 5 * 60 * 1000;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/**
	 * 봇 스캔이 IP 를 무한히 만들어내도 힙을 못 먹게 하는 상한.
	 * 실사용은 하루 수천 수준이라 닿을 일이 없고, 닿으면 그 시점부터 신규만 무시한다
	 * (이미 센 사용자는 유지 — 숫자가 과소집계될지언정 뒤로 가지는 않는다).
	 */
	private static final int MAX_TRACKED = 200_000;

	private final Map<String, Long> userActivity = new ConcurrentHashMap<>();

	/** 오늘 본 고유 사용자. 자정(KST)에 통째로 비운다. */
	private final Set<String> dailyUsers = ConcurrentHashMap.newKeySet();
	private volatile LocalDate dailyUsersDate = LocalDate.now(KST);

	private final Clock clock;

	public void recordUserActivity(String userIdentifier) {
		if (userActivity.size() < MAX_TRACKED || userActivity.containsKey(userIdentifier)) {
			userActivity.put(userIdentifier, clock.millis());
		}

		rollDateIfNeeded();
		if (dailyUsers.size() < MAX_TRACKED) {
			dailyUsers.add(userIdentifier);
		}
	}

	/** 5분 내에 활동한 고유 사용자 수. */
	public long getActiveUsersCount() {
		long now = clock.millis();
		return userActivity.values().stream()
			.filter(lastActivityTime -> (now - lastActivityTime) < ACTIVE_DURATION_MS)
			.count();
	}

	/** 오늘(KST) 한 번이라도 요청한 고유 사용자 수. */
	public long getDailyUsersCount() {
		rollDateIfNeeded();
		return dailyUsers.size();
	}

	/**
	 * 날짜가 넘어갔으면 일일 집합을 비운다.
	 *
	 * 스케줄러가 아니라 호출 시점에 확인한다. 이 빈은 웹 파드에서 사는데 스케줄러는
	 * 다른 파드라 {@code @Scheduled} 가 여기서는 돌지 않는다(APP_SCHEDULING_ENABLED=false).
	 */
	private void rollDateIfNeeded() {
		LocalDate today = LocalDate.now(clock.withZone(KST));
		if (!today.equals(dailyUsersDate)) {
			synchronized (this) {
				if (!today.equals(dailyUsersDate)) {
					dailyUsers.clear();
					dailyUsersDate = today;
				}
			}
		}
	}

	/** 5분이 지난 항목 정리. 활성 집계는 시간으로 거르므로 이건 순수 메모리 회수다. */
	public void cleanupOldUsers() {
		long now = clock.millis();
		userActivity.entrySet()
			.removeIf(entry -> (now - entry.getValue()) > ACTIVE_DURATION_MS);
	}
}
