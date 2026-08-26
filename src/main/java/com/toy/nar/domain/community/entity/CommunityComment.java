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
 * 커뮤니티 댓글. 깊이는 1단 — 답글의 답글은 parent 를 조부모로 올려붙이고
 * mention 으로 대상을 남긴다(유도는 서버가 replyToCommentId 로 한다).
 * 카운터 규칙은 {@link CommunityPost} 와 같다 — like_count 는 원자 SQL 로만.
 */
@Entity
@Table(name = "community_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	/** NULL = 최상위, 값 = 최상위 댓글의 id (1단 고정). */
	@Column(name = "parent_id")
	private Long parentId;

	@Column(name = "member_id")
	private Long memberId;

	@Column(name = "author_team_id")
	private Long authorTeamId;

	/** @닉네임 대상. FK 없는 소프트 참조 — 탈퇴하면 조회 실패로 "탈퇴한 사용자". */
	@Column(name = "mention_member_id")
	private Long mentionMemberId;

	@Column(nullable = false, length = 1000)
	private String body;

	@Column(name = "like_count", nullable = false)
	private int likeCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CommunityContentStatus status = CommunityContentStatus.VISIBLE;

	// 앱 시각으로 직접 넣는다 — CommunityPost.createdAt 과 같은 이유.
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	public CommunityComment(Long postId, Long parentId, Long memberId, Long authorTeamId,
			Long mentionMemberId, String body) {
		this.postId = postId;
		this.parentId = parentId;
		this.memberId = memberId;
		this.authorTeamId = authorTeamId;
		this.mentionMemberId = mentionMemberId;
		this.body = body;
		this.createdAt = LocalDateTime.now();
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
