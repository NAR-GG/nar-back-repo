package com.toy.nar.domain.community.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 커뮤니티 게시글. 설계는 docs/community-backend-design.md, DDL 은 V79.
 *
 * <p>카운터(view/like/comment)는 이 엔티티로 갱신하지 않는다 — JPA dirty checking 은
 * 읽은 값 기준 덮어쓰기라 동시 증감이 유실된다. 카운터는 전부
 * {@code UPDATE ... SET x = x + 1} 원자 SQL(RepositoryImpl)로만 만진다.</p>
 *
 * <p>연관관계 없이 id 만 든다(member_id, board_team_id). 목록·상세가 전부 조인 SQL 로
 * 내려가는 구조라 엔티티 그래프 순회가 없고, LAZY 프록시가 낄 자리도 없다.</p>
 */
@Entity
@Table(name = "community_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** NULL = 전체 게시판. */
	@Column(name = "board_team_id")
	private Long boardTeamId;

	/** 작성자. 회원 하드 삭제 시 DB 가 SET NULL — "탈퇴한 사용자". */
	@Column(name = "member_id")
	private Long memberId;

	/** 작성 시점 응원팀 스냅샷. 팀을 옮겨도 과거 글의 뱃지는 안 바뀐다. */
	@Column(name = "author_team_id")
	private Long authorTeamId;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, columnDefinition = "MEDIUMTEXT")
	private String body;

	/** PLAIN = 평문, BLOCKS = 블록 JSON(V83). 렌더러가 이 값으로 body 해석을 가른다. */
	@Column(name = "body_format", nullable = false, length = 10)
	private String bodyFormat = "PLAIN";

	/** BLOCKS 글의 목록 미리보기(저장 시 계산). PLAIN 은 null — body 절단으로 만든다. */
	@Column(length = 300)
	private String preview;

	@Column(name = "view_count", nullable = false)
	private int viewCount;

	@Column(name = "like_count", nullable = false)
	private int likeCount;

	@Column(name = "comment_count", nullable = false)
	private int commentCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CommunityContentStatus status = CommunityContentStatus.VISIBLE;

	/** 본문·제목 수정 시각. status 변경에는 안 찍는다 — "수정됨" 오탐 방지. */
	@Column(name = "edited_at")
	private LocalDateTime editedAt;

	// DB DEFAULT 가 있지만 앱 시각을 직접 넣는다 — DB 서버는 UTC 라 기존 알림 경로와
	// 같은 이유(member_notification 참고)로 KST 정렬이 섞이지 않게 한다.
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	public CommunityPost(Long boardTeamId, Long memberId, Long authorTeamId, String title, String body,
			String bodyFormat, String preview) {
		this.boardTeamId = boardTeamId;
		this.memberId = memberId;
		this.authorTeamId = authorTeamId;
		this.title = title;
		this.body = body;
		this.bodyFormat = bodyFormat == null ? "PLAIN" : bodyFormat;
		this.preview = preview;
		this.createdAt = LocalDateTime.now();
	}

	public void edit(String title, String body, String bodyFormat, String preview) {
		this.title = title;
		this.body = body;
		this.bodyFormat = bodyFormat == null ? "PLAIN" : bodyFormat;
		this.preview = preview;
		this.editedAt = LocalDateTime.now();
	}

	public void softDelete() {
		this.status = CommunityContentStatus.DELETED;
	}

	public boolean isVisible() {
		return this.status == CommunityContentStatus.VISIBLE;
	}

	public boolean isAuthor(Long candidateMemberId) {
		return this.memberId != null && this.memberId.equals(candidateMemberId);
	}
}
