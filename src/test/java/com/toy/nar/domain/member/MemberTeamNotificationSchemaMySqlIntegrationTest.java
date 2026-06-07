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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MemberTeamNotificationSchemaMySqlIntegrationTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
			.withDatabaseName("nar_notification_test")
			.withUsername("test")
			.withPassword("test")
			.withInitScript("db/pre_v31_schema.sql");

	@Test
	void migratesExistingFavoriteTeamAndPassesHibernateValidation() throws Exception {
		flyway(MigrationVersion.fromVersion("38")).migrate();
		seedExistingMember();

		var result = flyway(null).migrate();

		assertThat(result.targetSchemaVersion).isEqualTo("39");
		assertMigratedSubscription();
		validateSubscriptionSchemaWithHibernate();
	}

	private Flyway flyway(MigrationVersion target) {
		var configuration = Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion(MigrationVersion.fromVersion("30"));
		if (target != null) {
			configuration.target(target);
		}
		return configuration.load();
	}

	private void seedExistingMember() throws Exception {
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO teams (team_id, team_name, team_code, team_image_url)
					VALUES (1, 'T1', 'T1', 't1.png')
					""");
			statement.executeUpdate("""
					INSERT INTO teams (team_id, team_name, team_code, team_image_url)
					VALUES (2, 'G2 Esports', 'G2', 'g2.png')
					""");
			statement.executeUpdate("""
					INSERT INTO member (id, nickname, email, favorite_team_id, created_at, favorite_league_name)
					VALUES (7, '용맹한바론', 'test@example.com', 1, NOW(), 'LCK')
					""");
			statement.executeUpdate("""
					INSERT INTO member (id, nickname, email, favorite_team_id, created_at, favorite_league_name)
					VALUES (8, '유럽팬', 'lec@example.com', 2, NOW(), 'LEC')
					""");
		}
	}

	private void assertMigratedSubscription() throws Exception {
		try (Connection connection = connection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("""
						SELECT member_id, team_id, set_start_enabled, set_end_enabled, live_event_enabled
						FROM member_team_notification_subscription
						WHERE member_id = 7 AND team_id = 1
						""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getLong("member_id")).isEqualTo(7L);
			assertThat(resultSet.getLong("team_id")).isEqualTo(1L);
			assertThat(resultSet.getBoolean("set_start_enabled")).isTrue();
			assertThat(resultSet.getBoolean("set_end_enabled")).isTrue();
			assertThat(resultSet.getBoolean("live_event_enabled")).isFalse();
			assertThat(resultSet.next()).isFalse();
		}
		try (Connection connection = connection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("""
						SELECT COUNT(*) AS subscription_count
						FROM member_team_notification_subscription
						""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getLong("subscription_count")).isEqualTo(1L);
		}
	}

	private void validateSubscriptionSchemaWithHibernate() {
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
					.addAnnotatedClass(SubscriptionSchemaProbe.class)
					.buildMetadata()
					.buildSessionFactory()
					.close();
		} finally {
			StandardServiceRegistryBuilder.destroy(registry);
		}
	}

	private Connection connection() throws Exception {
		return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
	}

	@Entity(name = "SubscriptionSchemaProbe")
	@Table(name = "member_team_notification_subscription")
	static class SubscriptionSchemaProbe {

		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;

		@Column(name = "member_id", nullable = false)
		private Long memberId;

		@Column(name = "team_id", nullable = false)
		private Long teamId;

		@Column(name = "set_start_enabled", nullable = false)
		private boolean setStartEnabled;

		@Column(name = "set_end_enabled", nullable = false)
		private boolean setEndEnabled;

		@Column(name = "live_event_enabled", nullable = false)
		private boolean liveEventEnabled;

		@Column(name = "created_at", nullable = false)
		private LocalDateTime createdAt;

		@Column(name = "updated_at", nullable = false)
		private LocalDateTime updatedAt;
	}
}
