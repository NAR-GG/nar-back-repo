package com.toy.nar.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 스케줄러 전역 스위치. 이 설정이 안 뜨면 애플리케이션의 모든 {@code @Scheduled} 가 등록되지 않는다.
 *
 * <p>기본값 OFF. prod 프로파일(application-prod.yml)에서만 app.scheduling.enabled=true 로 켠다.
 * 로컬은 DB가 prod 데이터 사본이고 YouTube/Riot API 키·Discord 웹훅·FCM 자격증명을 prod와 공유하기 때문에,
 * 로컬에서 스케줄러가 돌면 prod의 API 쿼터를 태우거나 prod 채널·실유저에게 알림이 나갈 수 있다.
 * 로컬에서 스케줄러를 검증해야 하면 app.scheduling.enabled=true 로 켠다(공유 자원 영향 확인 후).
 * 개별 잡 단위 검증은 백오피스 수동 트리거 API를 쓰면 스케줄러를 켜지 않아도 된다.
 */
@Configuration
@Profile("!benchmark")
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true")
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

	/**
	 * 잡을 동시에 돌리는 스레드 수. 잡 수(30개) 이상으로 잡아 cron 이 몰려도 라이브 폴링이 밀리지
	 * 않게 한다. 가상 스레드라 비용이 없다. 잡을 늘려 이 수를 넘기면 같이 올린다.
	 *
	 * <p>SimpleAsyncTaskScheduler 를 쓰지 않는 이유 — 그건 fixedDelay 잡을 <b>스케줄러 스레드 하나</b>에서
	 * 차례로 돌린다. 2026-11-02 16:08 솔랭 종료 알림의 FCM 발송 하나가 영영 안 끝나자 그 스레드를 쓰던
	 * fixedDelay 잡 전부(솔랭 모니터·종료 알림·티어 갱신·라이브 폴링)가 22시간 섰다. 풀이면 잡 하나가
	 * 막혀도 스레드 하나만 잃는다. 한 잡이 자기 자신과 겹치지 않는 fixedDelay 의미는 그대로지만,
	 * fixedDelay 잡끼리는 이제 동시에 돈다(공유 상태는 ConcurrentHashMap, 발송은 DB 예약으로 멱등).</p>
	 */
	private static final int SCHEDULER_POOL_SIZE = 32;

	private final SchedulerLeaseService leaseService;

	public SchedulerConfig(SchedulerLeaseService leaseService) {
		this.leaseService = leaseService;
	}

	@Bean
	public TaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.setVirtualThreads(true);
		taskScheduler.setPoolSize(SCHEDULER_POOL_SIZE);
		taskScheduler.setThreadNamePrefix("Scheduled-VT-");
		return taskScheduler;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		// 모든 @Scheduled 가 이 스케줄러를 거친다. 리더 게이트를 여기 한 곳에 두면
		// 잡을 새로 추가해도 게이트를 빠뜨릴 수 없다 (LeaderGatedTaskScheduler 참고).
		taskRegistrar.setTaskScheduler(new LeaderGatedTaskScheduler(taskScheduler(), leaseService));
	}
}
