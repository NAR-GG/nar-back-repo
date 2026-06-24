package com.toy.nar.domain.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MobilePushSchemaMySqlIntegrationTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
			.withDatabaseName("nar_mobile_push_test")
			.withUsername("test")
			.withPassword("test")
			.withInitScript("db/pre_v31_schema.sql");

	@Test
	void migratesMobilePushTablesAndPassesHibernateValidation() {
		Flyway flyway = Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion(MigrationVersion.fromVersion("30"))
				.load();

		var result = flyway.migrate();

		// 최신 마이그레이션까지 깨끗이 적용됐는지만 확인한다.
		// (특정 버전 하드코딩은 새 마이그레이션마다 깨지므로 success로 검증)
		assertThat(result.success).isTrue();
		validateWithHibernate();
	}

	private void validateWithHibernate() {
		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
				.applySetting("jakarta.persistence.jdbc.url", MYSQL.getJdbcUrl())
				.applySetting("jakarta.persistence.jdbc.user", MYSQL.getUsername())
				.applySetting("jakarta.persistence.jdbc.password", MYSQL.getPassword())
				.applySetting("jakarta.persistence.jdbc.driver", MYSQL.getDriverClassName())
				.applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
				.applySetting("hibernate.hbm2ddl.auto", "validate")
				.build();
		try {
			new MetadataSources(registry)
					.addAnnotatedClass(MemberDeviceSchemaProbe.class)
					.addAnnotatedClass(PushDeliverySchemaProbe.class)
					.addAnnotatedClass(MemberNotificationSchemaProbe.class)
					.buildMetadata()
					.buildSessionFactory()
					.close();
		} finally {
			StandardServiceRegistryBuilder.destroy(registry);
		}
	}

	@Entity(name = "MemberDeviceSchemaProbe")
	@Table(name = "member_device")
	static class MemberDeviceSchemaProbe {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;
		@Column(name = "member_id", nullable = false)
		private Long memberId;
		@Column(name = "fcm_token", nullable = false, length = 512)
		private String fcmToken;
		@Column(name = "platform", nullable = false, length = 20)
		private String platform;
		@Column(name = "active", nullable = false)
		private boolean active;
		@Column(name = "last_registered_at", nullable = false)
		private LocalDateTime lastRegisteredAt;
		@Column(name = "created_at", nullable = false)
		private LocalDateTime createdAt;
		@Column(name = "updated_at", nullable = false)
		private LocalDateTime updatedAt;
	}

	@Entity(name = "MemberNotificationSchemaProbe")
	@Table(name = "member_notification")
	static class MemberNotificationSchemaProbe {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;
		@Column(name = "member_id", nullable = false)
		private Long memberId;
		@Column(name = "type", nullable = false, length = 40)
		private String type;
		@Column(name = "title", nullable = false, length = 255)
		private String title;
		@Column(name = "body", length = 500)
		private String body;
		@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
		@Column(name = "data")
		private java.util.Map<String, String> data;
		@Column(name = "read_at")
		private LocalDateTime readAt;
		@Column(name = "created_at", nullable = false)
		private LocalDateTime createdAt;
	}

	@Entity(name = "PushDeliverySchemaProbe")
	@Table(name = "player_solo_rank_push_delivery")
	static class PushDeliverySchemaProbe {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;
		@Column(name = "member_id", nullable = false)
		private Long memberId;
		@Column(name = "player_id", nullable = false)
		private Long playerId;
		@Column(name = "game_id", nullable = false, length = 64)
		private String gameId;
		@Column(name = "status", nullable = false, length = 20)
		private String status;
		@Column(name = "error_message", length = 500)
		private String errorMessage;
		@Column(name = "sent_at")
		private LocalDateTime sentAt;
		@Column(name = "created_at", nullable = false)
		private LocalDateTime createdAt;
		@Column(name = "updated_at", nullable = false)
		private LocalDateTime updatedAt;
	}
}
