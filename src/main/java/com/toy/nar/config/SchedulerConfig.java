package com.toy.nar.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
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

	private static final int SCHEDULED_TASK_CONCURRENCY_LIMIT = 5;

	private final SchedulerLeaseService leaseService;

	public SchedulerConfig(SchedulerLeaseService leaseService) {
		this.leaseService = leaseService;
	}

	@Bean
	public TaskScheduler taskScheduler() {
		SimpleAsyncTaskScheduler taskScheduler = new SimpleAsyncTaskScheduler();
		taskScheduler.setVirtualThreads(true);
		taskScheduler.setConcurrencyLimit(SCHEDULED_TASK_CONCURRENCY_LIMIT);
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
