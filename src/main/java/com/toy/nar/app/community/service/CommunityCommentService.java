package com.toy.nar.app.community.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.community.dto.CommunityDtos.AuthorResponse;
import com.toy.nar.app.community.dto.CommunityDtos.CommentCreateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.CommentListResponse;
import com.toy.nar.app.community.dto.CommunityDtos.CommentResponse;
import com.toy.nar.app.community.dto.CommunityDtos.LikeToggleResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.community.entity.CommunityComment;
import com.toy.nar.domain.community.entity.CommunityPost;
import com.toy.nar.domain.community.repository.CommunityCommentRepository;
import com.toy.nar.domain.community.repository.CommunityCommentRow;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository.ToggleResult;
import com.toy.nar.domain.community.repository.CommunityPostRepository;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCommentService {

	private static final int DEFAULT_PAGE_SIZE = 50;
	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_BODY_LENGTH = 1000;

	private final CommunityCommentRepository commentRepository;
	private final CommunityPostRepository postRepository;
	private final CommunityInteractionRepository interactionRepository;
	private final MemberRepository memberRepository;
	private final CommunityWriteGuard writeGuard;

	public CommentListResponse getComments(long postId, Long cursor, Integer size, Long viewerId) {
		requireVisiblePost(postId);
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
		List<CommunityCommentRow> rows = commentRepository.findPage(postId, cursor, pageSize);

		Set<Long> blocked = viewerId == null
				? Set.of()
				: Set.copyOf(interactionRepository.findBlockedMemberIds(viewerId));
		Set<Long> liked = viewerId == null
				? Set.of()
				: interactionRepository.findLikedCommentIds(viewerId, rows.stream().map(CommunityCommentRow::id).toList());

		List<CommentResponse> comments = rows.stream()
				.map(row -> toResponse(row, viewerId, blocked, liked))
				.toList();
		Long nextCursor = rows.size() < pageSize ? null : rows.get(rows.size() - 1).id();
		return new CommentListResponse(comments, nextCursor);
	}

	@Transactional
	public long create(long postId, Long memberId, CommentCreateRequest request) {
		Member member = requireMember(memberId);
		String body = CommunityPostService.requireLength(request.body(), MAX_BODY_LENGTH);
		CommunityPost post = requireVisiblePost(postId);
		// 팀 게시판이면 댓글도 응원팀·쿨다운 검사를 탄다 — 쓰기 전반이 같은 규칙(D-1).
		writeGuard.checkBoardWritable(member, post.getBoardTeamId());
		writeGuard.checkCommentInterval(memberId);

		Long parentId = null;
		Long mentionMemberId = null;
		if (request.replyToCommentId() != null) {
			// parent 올려붙이기(1단 고정)와 멘션 대상은 서버가 유도한다 — 앱이 직접 보내면
			// API 호출로 관계없는 회원을 멘션에 꽂거나 깊이 규칙이 두 군데 살게 된다.
			CommunityComment target = commentRepository.findById(request.replyToCommentId())
					.filter(c -> c.getPostId().equals(postId))
					.filter(CommunityComment::isVisible)
					.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
			parentId = target.getParentId() != null ? target.getParentId() : target.getId();
			mentionMemberId = target.getMemberId();
		}

		Long authorTeamId = member.getFavoriteTeam() == null ? null : member.getFavoriteTeam().getId();
		CommunityComment comment = CommunityComment.builder()
				.postId(postId)
				.parentId(parentId)
				.memberId(memberId)
				.authorTeamId(authorTeamId)
				.mentionMemberId(mentionMemberId)
				.body(body)
				.build();
		long id = commentRepository.save(comment).getId();
		// 자식 insert → 부모 카운터 순서 고정(락 수칙). 같은 트랜잭션이라 정합.
		postRepository.applyCommentDelta(postId, 1);
		return id;
	}

	@Transactional
	public void delete(long commentId, Long memberId) {
		CommunityPostService.requireLogin(memberId);
		CommunityComment comment = commentRepository.findById(commentId)
				.filter(CommunityComment::isVisible)
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
		if (!comment.isAuthor(memberId)) {
			throw new CustomException(ErrorCode.COMMUNITY_NOT_AUTHOR);
		}
		comment.softDelete();
		// 목록 숫자 ≠ 상세 개수가 되지 않게 소프트 삭제도 카운터를 내린다(설계 문서 7장).
		postRepository.applyCommentDelta(comment.getPostId(), -1);
	}

	@Transactional
	public LikeToggleResponse toggleLike(long commentId, Long memberId) {
		CommunityPostService.requireLogin(memberId);
		commentRepository.findById(commentId)
				.filter(CommunityComment::isVisible)
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
		ToggleResult result = interactionRepository.toggleCommentLike(commentId, memberId);
		int likeCount = switch (result) {
			case ADDED -> commentRepository.applyLikeDelta(commentId, 1);
			case REMOVED -> commentRepository.applyLikeDelta(commentId, -1);
			case ALREADY_ADDED -> commentRepository.applyLikeDelta(commentId, 0);
		};
		return new LikeToggleResponse(result != ToggleResult.REMOVED, likeCount);
	}

	public com.toy.nar.app.community.dto.CommunityDtos.MyCommentListResponse getMyComments(
			Long memberId, Long cursor, Integer size) {
		CommunityPostService.requireLogin(memberId);
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
		var rows = commentRepository.findMyCommentPage(memberId, cursor, pageSize);
		var comments = rows.stream()
				.map(row -> new com.toy.nar.app.community.dto.CommunityDtos.MyCommentResponse(
						row.id(), row.postId(), row.postTitle(), row.body(), row.likeCount(), row.createdAt()))
				.toList();
		Long nextCursor = rows.size() < pageSize ? null : rows.get(rows.size() - 1).id();
		return new com.toy.nar.app.community.dto.CommunityDtos.MyCommentListResponse(comments, nextCursor);
	}

	/* ---------- 내부 ---------- */

	private CommentResponse toResponse(CommunityCommentRow row, Long viewerId, Set<Long> blocked, Set<Long> liked) {
		boolean mine = viewerId != null && viewerId.equals(row.authorMemberId());
		String status = row.status();
		// 차단한 작성자의 댓글은 자리만 남긴다(D-5). 삭제·블라인드보다 뒤에 판정해도
		// 결과는 같다 — VISIBLE 이 아닌 댓글은 어차피 body 를 안 내린다.
		if ("VISIBLE".equals(status) && row.authorMemberId() != null && blocked.contains(row.authorMemberId())) {
			status = "BLOCKED";
		}
		boolean visible = "VISIBLE".equals(status);

		AuthorResponse author = null;
		if (visible && row.authorMemberId() != null) {
			author = new AuthorResponse(row.authorMemberId(), row.authorName() + "#" + row.authorTag(),
					row.authorProfileImageUrl(), row.authorTeamId(), row.authorTeamCode(), row.authorTeamImageUrl());
		}
		String mentionNickname = null;
		if (visible && row.mentionMemberId() != null && row.mentionName() != null) {
			mentionNickname = row.mentionName() + "#" + row.mentionTag();
		}
		return new CommentResponse(row.id(), row.parentId(),
				visible ? row.body() : null, status, author, mentionNickname,
				row.likeCount(), liked.contains(row.id()), mine, row.createdAt());
	}

	private CommunityPost requireVisiblePost(long postId) {
		return postRepository.findById(postId)
				.filter(CommunityPost::isVisible)
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
	}

	private Member requireMember(Long memberId) {
		CommunityPostService.requireLogin(memberId);
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_LOGIN_REQUIRED));
	}
}
