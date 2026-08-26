package com.toy.nar.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.toy.nar.domain.community.entity.CommunityReport.TargetType;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository.ToggleResult;
import com.toy.nar.domain.community.repository.CommunityModerationRepository;
import com.toy.nar.domain.community.repository.CommunityPostRepositoryImpl;
import com.toy.nar.domain.community.repository.CommunityPostRow;

/**
 * 커뮤니티 목록의 핵심 로직을 실제 MySQL 에서 검증한다 — 커서 페이지네이션,
 * 차단 작성자 필터(동적 IN), 전체/팀 게시판 분기, 좋아요 토글의 멱등성.
 * Spring 컨텍스트 없이 JdbcTemplate 직결이라 가볍다.
 */
@Testcontainers(disabledWithoutDocker = true)
class CommunityRepositoryMySqlIntegrationTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
			.withDatabaseName("nar_community_test")
			.withUsername("test")
			.withPassword("test")
			.withInitScript("db/pre_v31_schema.sql");

	static JdbcTemplate jdbc;
	static CommunityPostRepositoryImpl postRepository;
	static CommunityInteractionRepository interactions;

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("30")
				.load()
				.migrate();
		jdbc = new JdbcTemplate(new DriverManagerDataSource(
				MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
		postRepository = new CommunityPostRepositoryImpl(jdbc);
		interactions = new CommunityInteractionRepository(jdbc);

		jdbc.update("INSERT INTO teams (team_id, team_name, team_code) VALUES (1, 'T1', 'T1')");
		jdbc.update("INSERT INTO member (id, name, tag, role, created_at, quiet_hours_enabled,"
				+ " quiet_start_time, quiet_end_time)"
				+ " VALUES (1, '작성자', '0001', 'USER', NOW(), 0, '01:00', '08:00'),"
				+ " (2, '차단당한사람', '0002', 'USER', NOW(), 0, '01:00', '08:00'),"
				+ " (3, '보는사람', '0003', 'USER', NOW(), 0, '01:00', '08:00')");
		// 전체 게시판 글 5개 — member 1 이 3개(id 1,3,5), member 2 가 2개(id 2,4). 팀 게시판 글 1개(id 6).
		for (int i = 1; i <= 5; i++) {
			jdbc.update("INSERT INTO community_post (id, board_team_id, member_id, title, body)"
					+ " VALUES (?, NULL, ?, ?, 'body')", i, (i % 2 == 1) ? 1 : 2, "글" + i);
		}
		jdbc.update("INSERT INTO community_post (id, board_team_id, member_id, title, body)"
				+ " VALUES (6, 1, 1, '팀글', 'body')");
		jdbc.update("UPDATE community_post SET status = 'DELETED' WHERE id = 5");
		jdbc.update("INSERT INTO member_block (member_id, blocked_member_id) VALUES (3, 2)");
	}

	@Test
	void 전체게시판_커서와_차단필터가_같이_돈다() {
		List<Long> blocked = interactions.findBlockedMemberIds(3L);
		assertThat(blocked).containsExactly(2L);

		// 첫 페이지: 삭제(5)·팀글(6)·차단 작성자 글(2,4) 제외 → 3, 1
		List<CommunityPostRow> page = postRepository.findPage(null, null, blocked, 2);
		assertThat(page).extracting(CommunityPostRow::id).containsExactly(3L, 1L);

		// 커서로 이어 읽기 — 남은 게 없다
		List<CommunityPostRow> next = postRepository.findPage(null, 1L, blocked, 2);
		assertThat(next).isEmpty();

		// 차단 없는 사용자는 4개 전부(4,3,2,1) — 삭제·팀글만 빠진다
		List<CommunityPostRow> all = postRepository.findPage(null, null, List.of(), 10);
		assertThat(all).extracting(CommunityPostRow::id).containsExactly(4L, 3L, 2L, 1L);
	}

	@Test
	void 팀게시판은_보드분기로만_나온다() {
		List<CommunityPostRow> teamPage = postRepository.findPage(1L, null, List.of(), 10);
		assertThat(teamPage).extracting(CommunityPostRow::id).containsExactly(6L);
		assertThat(teamPage.get(0).authorTeamId()).isNull(); // author_team_id 는 안 넣었다
	}

	@Test
	void 좋아요_토글은_멱등이고_카운터와_같이_움직인다() {
		assertThat(interactions.togglePostLike(1L, 3L)).isEqualTo(ToggleResult.ADDED);
		assertThat(postRepository.applyLikeDelta(1L, 1)).isEqualTo(1);

		assertThat(interactions.togglePostLike(1L, 3L)).isEqualTo(ToggleResult.REMOVED);
		assertThat(postRepository.applyLikeDelta(1L, -1)).isEqualTo(0);

		// 0 밑으로 안 내려간다 (GREATEST 가드)
		assertThat(postRepository.applyLikeDelta(1L, -1)).isEqualTo(0);
	}

	@Test
	void 차단은_멱등이고_신고대상_검증은_VISIBLE만_통과한다() {
		CommunityModerationRepository moderation = new CommunityModerationRepository(jdbc);

		assertThat(moderation.insertBlock(1L, 2L)).isTrue();
		assertThat(moderation.insertBlock(1L, 2L)).isFalse(); // 중복 차단 멱등
		assertThat(moderation.deleteBlock(1L, 2L)).isTrue();
		assertThat(moderation.deleteBlock(1L, 2L)).isFalse(); // 중복 해제 멱등

		assertThat(moderation.findVisibleTargetPreview(TargetType.POST, 1L)).isPresent();
		assertThat(moderation.findVisibleTargetPreview(TargetType.POST, 5L)).isEmpty();   // DELETED
		assertThat(moderation.findVisibleTargetPreview(TargetType.POST, 999L)).isEmpty(); // 실존 안 함
		assertThat(moderation.findVisibleTargetPreview(TargetType.COMMENT, 1L)).isEmpty();
	}

	@Test
	void 첨부사진은_전체교체되고_VISIBLE만_내려간다() {
		postRepository.replaceImages(2L, List.of("https://res.cloudinary.com/x/a.jpg",
				"https://res.cloudinary.com/x/b.jpg"));
		assertThat(postRepository.findVisibleImages(2L)).hasSize(2);
		assertThat(postRepository.findVisibleImages(2L).get(0).imageUrl()).endsWith("a.jpg"); // sort_order 유지

		// 신고로 한 장만 블라인드 → 상세·목록에서 그 장만 빠진다
		jdbc.update("UPDATE community_post_image SET status = 'HIDDEN' WHERE post_id = 2 AND sort_order = 0");
		assertThat(postRepository.findVisibleImages(2L)).hasSize(1);
		assertThat(postRepository.findVisibleImagesByPostIds(List.of(2L, 3L))).hasSize(1);

		// 전체 교체 — 이전 행(HIDDEN 포함) 삭제 후 재삽입
		postRepository.replaceImages(2L, List.of("https://res.cloudinary.com/x/c.jpg"));
		assertThat(postRepository.findVisibleImages(2L)).hasSize(1);
		assertThat(postRepository.findVisibleImages(2L).get(0).imageUrl()).endsWith("c.jpg");

		postRepository.replaceImages(2L, List.of()); // 빈 배열 = 전부 제거
		assertThat(postRepository.findVisibleImages(2L)).isEmpty();
	}

	@Test
	void 작성간격_검사용_마지막작성시각은_삭제글도_본다() {
		// member 1 의 마지막 글은 삭제된 5번 — status 무관하게 잡혀야 우회가 막힌다
		assertThat(postRepository.findLastCreatedAt(1L)).isPresent();
		assertThat(postRepository.findLastCreatedAt(999L)).isEmpty();
	}
}
