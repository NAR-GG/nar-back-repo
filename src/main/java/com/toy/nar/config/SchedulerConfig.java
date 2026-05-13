package com.toy.nar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@Profile("!benchmark")
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

	private static final int SCHEDULED_TASK_CONCURRENCY_LIMIT = 5;

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
		taskRegistrar.setTaskScheduler(taskScheduler());
	}
}
