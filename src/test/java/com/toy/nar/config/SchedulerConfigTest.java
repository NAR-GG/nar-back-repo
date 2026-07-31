package com.toy.nar.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 스케줄러 전역 스위치가 실제로 스케줄링 등록을 좌우하는지 검증.
 * app.scheduling.enabled 가 없으면(로컬 기본) SchedulerConfig 자체가 뜨지 않아야 한다.
 */
class SchedulerConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(SchedulerConfig.class);

	@Test
	@DisplayName("app.scheduling.enabled 미설정이면 스케줄러 설정이 로드되지 않는다")
	void schedulingDisabledByDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(SchedulerConfig.class);
			assertThat(context).doesNotHaveBean(ScheduledTaskRegistrar.class);
		});
	}

	@Test
	@DisplayName("app.scheduling.enabled=false 면 스케줄러 설정이 로드되지 않는다")
	void schedulingDisabledExplicitly() {
		contextRunner
				.withPropertyValues("app.scheduling.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(SchedulerConfig.class));
	}

	@Test
	@DisplayName("app.scheduling.enabled=true 면 스케줄러 설정과 TaskScheduler 가 로드된다")
	void schedulingEnabled() {
		contextRunner
				.withPropertyValues("app.scheduling.enabled=true")
				.run(context -> {
					assertThat(context).hasSingleBean(SchedulerConfig.class);
					assertThat(context).hasBean("taskScheduler");
				});
	}
}
