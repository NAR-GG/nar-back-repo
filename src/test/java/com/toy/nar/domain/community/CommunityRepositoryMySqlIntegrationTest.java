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
import com.toy.nar.domain.community.repository.CommunityCommentRepositoryImpl;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository.ToggleResult;
import com.toy.nar.domain.community.repository.CommunityMyCommentRow;
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

	/**
	 * 게시판 코드는 board_team_id 로 teams 를 따로 조인해 온다 — 작성자 응원팀 조인(t)과
	 * 별개의 조인(bt)이다. 둘을 헷갈려 t 를 재사용하면 위 테스트처럼 author_team_id 가
	 * 비어 있는 데이터에서 조용히 null 이 되므로, 두 값을 같은 행에서 대조한다.
	 */
	@Test
	void 게시판코드는_작성자팀과_다른_조인에서_온다() {
		CommunityPostRow teamPost = postRepository.findPage(1L, null, List.of(), 10).get(0);
		assertThat(teamPost.boardTeamId()).isEqualTo(1L);
		assertThat(teamPost.boardTeamCode()).isEqualTo("T1");
		assertThat(teamPost.authorTeamId()).isNull();   // 작성자 응원팀은 비어 있는데
		assertThat(teamPost.authorTeamCode()).isNull(); // 게시판 코드는 나온다

		// 전체 게시판은 둘 다 null 이다 — LEFT JOIN 이라 행이 사라지지 않는다.
		CommunityPostRow allBoardPost = postRepository.findPage(null, null, List.of(), 1).get(0);
		assertThat(allBoardPost.boardTeamId()).isNull();
		assertThat(allBoardPost.boardTeamCode()).isNull();
	}

	/** 내가 쓴 댓글도 원글이 속한 게시판을 실어야 한다 — 목록에 게시판이 섞여 나오기 때문. */
	@Test
	void 내댓글은_원글의_게시판을_싣는다() {
		CommunityCommentRepositoryImpl comments = new CommunityCommentRepositoryImpl(jdbc);
		// 컨테이너와 DB 를 클래스 전체가 공유한다. id 는 다른 테스트(101~103)와 겹치면 안 된다.
		jdbc.update("INSERT INTO community_comment (id, post_id, member_id, body)"
				+ " VALUES (110, 6, 1, '팀글 댓글'), (111, 1, 1, '전체글 댓글')");

		List<CommunityMyCommentRow> rows = comments.findMyCommentPage(1L, null, 10);

		CommunityMyCommentRow onTeamBoard = rows.stream().filter(r -> r.id() == 110L).findFirst().orElseThrow();
		assertThat(onTeamBoard.boardTeamId()).isEqualTo(1L);
		assertThat(onTeamBoard.boardTeamCode()).isEqualTo("T1");

		CommunityMyCommentRow onAllBoard = rows.stream().filter(r -> r.id() == 111L).findFirst().orElseThrow();
		assertThat(onAllBoard.boardTeamId()).isNull();
		assertThat(onAllBoard.boardTeamCode()).isNull();
	}

	@Test
	void 글_알림_mute_토글이_판정과_같이_돈다() {
		assertThat(interactions.isNotificationMuted(3L, 1L)).isFalse(); // 기본 = 수신
		assertThat(interactions.toggleNotificationMute(3L, 1L))
				.isEqualTo(ToggleResult.ADDED); // 끔
		assertThat(interactions.isNotificationMuted(3L, 1L)).isTrue();
		assertThat(interactions.toggleNotificationMute(3L, 1L))
				.isEqualTo(ToggleResult.REMOVED); // 다시 켬
		assertThat(interactions.isNotificationMuted(3L, 1L)).isFalse();
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
	void 내활동_목록은_삭제된_것을_숨긴다() {
		// 내가 쓴 글: member 1 의 글 1,3(전체),6(팀) — 삭제된 5는 빠진다
		assertThat(postRepository.findMyPostPage(1L, null, 10))
				.extracting(CommunityPostRow::id).containsExactly(6L, 3L, 1L);

		// 좋아요한 글: member 2 가 3(VISIBLE)과 5(DELETED)를 좋아요 → 3만 나온다
		interactions.togglePostLike(3L, 2L);
		interactions.togglePostLike(5L, 2L);
		var liked = postRepository.findLikedPage(2L, null, 10);
		assertThat(liked).extracting(CommunityPostRow::id).containsExactly(3L);
		assertThat(liked.get(0).scrapId()).isNotNull(); // 커서 = like.id

		// 내가 쓴 댓글: VISIBLE 댓글만, 그리고 원글이 삭제된 댓글도 빠진다.
		// id 는 101+ — 차단 테스트가 "COMMENT 1 은 없다"를 가정하므로 낮은 id 를 쓰면
		// 실행 순서에 따라 그 테스트를 깨뜨린다(CI 실측).
		jdbc.update("INSERT INTO community_comment (id, post_id, member_id, body) VALUES (101, 3, 2, '보임')");
		jdbc.update("INSERT INTO community_comment (id, post_id, member_id, body, status)"
				+ " VALUES (102, 3, 2, '삭제됨', 'DELETED')");
		jdbc.update("INSERT INTO community_comment (id, post_id, member_id, body) VALUES (103, 5, 2, '원글삭제')");
		var comments = commentPage(2L);
		assertThat(comments).hasSize(1);
		assertThat(comments.get(0).postTitle()).isEqualTo("글3");
	}

	private static java.util.List<com.toy.nar.domain.community.repository.CommunityMyCommentRow> commentPage(long memberId) {
		return new com.toy.nar.domain.community.repository.CommunityCommentRepositoryImpl(jdbc)
				.findMyCommentPage(memberId, null, 10);
	}

	@Test
	void 작성간격_검사용_마지막작성시각은_삭제글도_본다() {
		// member 1 의 마지막 전체글은 삭제된 5번 — status 무관하게 잡혀야 우회가 막힌다
		assertThat(postRepository.findLastCreatedAt(1L, null)).isPresent();
		assertThat(postRepository.findLastCreatedAt(999L, null)).isEmpty();
	}

	@Test
	void 작성간격은_게시판별로_따로_잡힌다() {
		// member 1 은 전체(1,3,5)와 팀 1(6)에 썼고, member 2 는 전체(2,4)에만 썼다.
		assertThat(postRepository.findLastCreatedAt(1L, null)).isPresent();
		assertThat(postRepository.findLastCreatedAt(1L, 1L)).isPresent();
		// 팀 게시판에 한 번도 안 쓴 회원은 전체에 썼어도 팀 쪽은 비어야 한다 —
		// 안 그러면 전체에 쓴 직후 팀 게시판까지 같이 잠긴다.
		assertThat(postRepository.findLastCreatedAt(2L, null)).isPresent();
		assertThat(postRepository.findLastCreatedAt(2L, 1L)).isEmpty();
	}
}
