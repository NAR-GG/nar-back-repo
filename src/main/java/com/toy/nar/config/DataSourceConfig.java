package com.toy.nar.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;

import javax.sql.DataSource;

@Slf4j
@Configuration
public class DataSourceConfig {

	@Value("${spring.datasource.url}")
	private String dataSourceUrl;

	@Value("${spring.datasource.driver-class-name}")
	private String driverClassName;

	@Bean
	public DataSource dataSource() {
		log.info("Initializing DataSource. URL: {}, Driver: {}", dataSourceUrl, driverClassName);

		// SQLite 설정인데 URL이 SQLite가 아니면 강제 보정 (안전장치)
		if (dataSourceUrl == null || !dataSourceUrl.startsWith("jdbc:sqlite:")) {
			log.warn("Invalid SQLite URL detected: {}. Falling back to default 'jdbc:sqlite:lol_esports_analysis_test.db'", dataSourceUrl);
			dataSourceUrl = "jdbc:sqlite:lol_esports_analysis_test.db";
		}

		final SQLiteConfig config = new SQLiteConfig();
		config.setJournalMode(SQLiteConfig.JournalMode.WAL);
		config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
		// Busy Timeout 설정 (30초 대기)
		config.setBusyTimeout(30000);

		final HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setJdbcUrl(dataSourceUrl);
		hikariConfig.setDriverClassName("org.sqlite.JDBC"); // 드라이버 강제 지정 (안전)
		hikariConfig.setDataSourceProperties(config.toProperties());
		// SQLite는 커넥션 풀을 1개만 쓰는 것이 락 방지에 유리할 수 있지만, 
		// WAL 모드에서는 여러 개도 괜찮습니다. 일단 기본값 유지하거나 조절.
		hikariConfig.setMaximumPoolSize(1); // 확실한 락 방지를 위해 1로 제한 (선택 사항)

		return new HikariDataSource(hikariConfig);
	}
}
