package com.toy.nar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class FlywayConfig {

	/**
	 * 마이그레이션 실패가 schema history에 남으면 이후 기동이 전부 막히므로,
	 * 기동 시 repair(실패 기록 제거·체크섬 정렬) 후 migrate 한다.
	 * 마이그레이션 스크립트는 반드시 멱등하게 작성한다 (실패 후 재실행될 수 있음).
	 */
	@Bean
	public FlywayMigrationStrategy repairThenMigrateStrategy() {
		return flyway -> {
			log.info("Flyway repair 실행 (실패 기록 정리)");
			flyway.repair();
			flyway.migrate();
		};
	}
}
