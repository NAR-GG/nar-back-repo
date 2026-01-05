package com.toy.nar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		// 스케줄러 스레드 풀 크기 설정 (LoL 매치 동기화 + 커뮤니티 동기화 + 데이터 삭제 등 동시 실행 고려)
		taskScheduler.setPoolSize(5); 
		taskScheduler.setThreadNamePrefix("Scheduled-Task-");
		taskScheduler.initialize();
		taskRegistrar.setTaskScheduler(taskScheduler);
	}
}
