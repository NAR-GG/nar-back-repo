package com.toy.nar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

	/**
	 * 알림 잠자기 시각 판정용 시계. KST 고정 — 유저별 타임존은 두지 않는다.
	 * 테스트에서 시각을 고정할 수 있도록 빈으로 뺐다.
	 */
	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
