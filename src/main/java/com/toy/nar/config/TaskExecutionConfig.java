package com.toy.nar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class TaskExecutionConfig {

	private static final int APPLICATION_TASK_CONCURRENCY_LIMIT = 100;

	@Bean(name = "applicationTaskExecutor")
	public AsyncTaskExecutor applicationTaskExecutor() {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("App-VT-");
		executor.setVirtualThreads(true);
		executor.setConcurrencyLimit(APPLICATION_TASK_CONCURRENCY_LIMIT);
		return executor;
	}
}
