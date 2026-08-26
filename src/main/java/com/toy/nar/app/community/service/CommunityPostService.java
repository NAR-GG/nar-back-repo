package com.toy.nar.app.community.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.app.community.dto.CommunityDtos.LikeToggleResponse;
import com.toy.nar.app.community.dto.CommunityDtos.PostCreateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.PostDetailResponse;
import com.toy.nar.app.community.dto.CommunityDtos.PostListResponse;
import com.toy.nar.app.community.dto.CommunityDtos.PostSummaryResponse;
import com.toy.nar.app.community.dto.CommunityDtos.PostUpdateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.PostViewerResponse;
import com.toy.nar.app.community.dto.CommunityDtos.ScrapItemResponse;
import com.toy.nar.app.community.dto.CommunityDtos.ScrapListResponse;
import com.toy.nar.app.community.dto.CommunityDtos.ScrapToggleResponse;
import com.toy.nar.common.error.ErrorCode;
import com.toy.nar.common.error.exception.CustomException;
import com.toy.nar.domain.community.entity.CommunityPost;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository;
import com.toy.nar.domain.community.repository.CommunityInteractionRepository.ToggleResult;
import com.toy.nar.domain.community.repository.CommunityPostRepository;
import com.toy.nar.domain.community.repository.CommunityPostRow;
import com.toy.nar.domain.member.entity.Member;
import com.toy.nar.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostService {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 50;
	private static final int PREVIEW_LENGTH = 150;
	private static final int MAX_TITLE_LENGTH = 100;
	private static final int MAX_BODY_LENGTH = 10_000;
	private static final int MAX_IMAGES = 5;
	private static final int MAX_IMAGE_URL_LENGTH = 500; // 컬럼 상한

	private final CommunityPostRepository postRepository;
	private final CommunityInteractionRepository interactionRepository;
	private final MemberRepository memberRepository;
	private final CommunityWriteGuard writeGuard;
	private final com.toy.nar.app.auth.profile.CloudinarySignatureService cloudinarySignatureService;

	public PostListResponse getPosts(Long boardTeamId, Long cursor, Integer size, Long viewerId) {
		int pageSize = clampSize(size);
		List<Long> blocked = viewerId == null
				? List.of()
				: interactionRepository.findBlockedMemberIds(viewerId);
		List<CommunityPostRow> rows = postRepository.findPage(boardTeamId, cursor, blocked, pageSize);
		var imagesByPost = imagesByPost(rows);
		List<PostSummaryResponse> posts = rows.stream()
				.map(row -> toSummary(row, imagesByPost.getOrDefault(row.id(), List.of())))
				.toList();
		return new PostListResponse(posts, nextCursor(rows, pageSize));
	}

	public PostDetailResponse getPost(long postId, Long viewerId) {
		CommunityPostRow row = postRepository.findRowById(postId)
				.filter(r -> "VISIBLE".equals(r.status()))
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

		boolean liked = false;
		boolean scrapped = false;
		boolean mine = false;
		boolean blockedAuthor = false;
		if (viewerId != null) {
			liked = interactionRepository.existsPostLike(postId, viewerId);
			scrapped = interactionRepository.existsScrap(postId, viewerId);
			mine = viewerId.equals(row.authorMemberId());
			// 목록은 숨기지만 상세 직진입(딥링크·스크랩 경유)은 자리만 마스킹해서 내려준다.
			blockedAuthor = row.authorMemberId() != null
					&& interactionRepository.findBlockedMemberIds(viewerId).contains(row.authorMemberId());
		}
		PostViewerResponse viewer = new PostViewerResponse(liked, scrapped, mine, blockedAuthor);
		List<com.toy.nar.app.community.dto.CommunityDtos.PostImageResponse> images = blockedAuthor
				? List.of()
				: postRepository.findVisibleImages(postId).stream()
						.map(img -> new com.toy.nar.app.community.dto.CommunityDtos.PostImageResponse(
								img.id(), img.imageUrl()))
						.toList();
		return new PostDetailResponse(row.id(), row.boardTeamId(),
				blockedAuthor ? null : row.title(),
				blockedAuthor ? null : row.body(),
				toAuthor(row), row.viewCount(), row.likeCount(), row.commentCount(),
				row.editedAt() != null, row.createdAt(), viewer, images);
	}

	@Transactional
	public long create(Long memberId, PostCreateRequest request) {
		Member member = requireMember(memberId);
		String title = requireLength(request.title(), MAX_TITLE_LENGTH);
		String body = requireLength(request.body(), MAX_BODY_LENGTH);
		writeGuard.checkBoardWritable(member, request.boardTeamId());
		writeGuard.checkPostInterval(memberId);

		List<String> imageUrls = validateImageUrls(request.imageUrls());

		Long authorTeamId = member.getFavoriteTeam() == null ? null : member.getFavoriteTeam().getId();
		CommunityPost post = CommunityPost.builder()
				.boardTeamId(request.boardTeamId())
				.memberId(memberId)
				.authorTeamId(authorTeamId)
				.title(title)
				.body(body)
				.build();
		long postId = postRepository.save(post).getId();
		if (!imageUrls.isEmpty()) {
			postRepository.replaceImages(postId, imageUrls);
		}
		return postId;
	}

	@Transactional
	public void update(long postId, Long memberId, PostUpdateRequest request) {
		requireLogin(memberId);
		CommunityPost post = requireVisiblePost(postId);
		if (!post.isAuthor(memberId)) {
			throw new CustomException(ErrorCode.COMMUNITY_NOT_AUTHOR);
		}
		post.edit(requireLength(request.title(), MAX_TITLE_LENGTH), requireLength(request.body(), MAX_BODY_LENGTH));
		// null = 이미지 변경 없음, 빈 배열 = 전부 제거 (DTO 계약)
		if (request.imageUrls() != null) {
			postRepository.replaceImages(postId, validateImageUrls(request.imageUrls()));
		}
	}

	@Transactional
	public void delete(long postId, Long memberId) {
		requireLogin(memberId);
		CommunityPost post = requireVisiblePost(postId);
		if (!post.isAuthor(memberId)) {
			throw new CustomException(ErrorCode.COMMUNITY_NOT_AUTHOR);
		}
		post.softDelete();
	}

	/**
	 * 조회수 +1. UPDATE 한 문장짜리 최소 트랜잭션 — 클래스 기본이 readOnly 라 여기만 쓰기로
	 * 연다. 다른 쓰기와 묶지 않으니 락은 찰나고, 틀려도 되는 숫자라 실패는 삼킨다(설계 문서 2장).
	 */
	@Transactional
	public void increaseViewCount(long postId) {
		try {
			postRepository.increaseViewCount(postId);
		} catch (Exception e) {
			log.warn("[community] view_count 증가 실패 postId={}", postId, e);
		}
	}

	@Transactional
	public LikeToggleResponse toggleLike(long postId, Long memberId) {
		requireLogin(memberId);
		requireVisiblePost(postId);
		ToggleResult result = interactionRepository.togglePostLike(postId, memberId);
		int likeCount = switch (result) {
			case ADDED -> postRepository.applyLikeDelta(postId, 1);
			case REMOVED -> postRepository.applyLikeDelta(postId, -1);
			case ALREADY_ADDED -> postRepository.applyLikeDelta(postId, 0);
		};
		return new LikeToggleResponse(result != ToggleResult.REMOVED, likeCount);
	}

	@Transactional
	public ScrapToggleResponse toggleScrap(long postId, Long memberId) {
		requireLogin(memberId);
		requireVisiblePost(postId);
		ToggleResult result = interactionRepository.toggleScrap(postId, memberId);
		return new ScrapToggleResponse(result != ToggleResult.REMOVED);
	}

	public ScrapListResponse getMyScraps(Long memberId, Long cursor, Integer size) {
		requireLogin(memberId);
		int pageSize = clampSize(size);
		List<CommunityPostRow> rows = postRepository.findScrapPage(memberId, cursor, pageSize);
		var imagesByPost = imagesByPost(rows);
		List<ScrapItemResponse> items = rows.stream()
				.map(row -> new ScrapItemResponse(row.scrapId(),
						toSummary(row, imagesByPost.getOrDefault(row.id(), List.of()))))
				.toList();
		Long nextCursor = rows.size() < pageSize ? null : rows.get(rows.size() - 1).scrapId();
		return new ScrapListResponse(items, nextCursor);
	}

	/* ---------- 내부 ---------- */

	static PostSummaryResponse toSummary(CommunityPostRow row,
			List<com.toy.nar.domain.community.repository.CommunityPostImageRow> images) {
		String preview = row.body().length() <= PREVIEW_LENGTH
				? row.body()
				: row.body().substring(0, PREVIEW_LENGTH);
		String thumbnailUrl = images.isEmpty() ? null : images.get(0).imageUrl();
		return new PostSummaryResponse(row.id(), row.boardTeamId(), row.title(), preview,
				toAuthor(row), row.viewCount(), row.likeCount(), row.commentCount(),
				row.editedAt() != null, row.createdAt(), thumbnailUrl, images.size());
	}

	/** 페이지의 글 id 들로 VISIBLE 사진을 한 방에 긁어 post_id 로 접는다(썸네일·개수용). */
	private java.util.Map<Long, List<com.toy.nar.domain.community.repository.CommunityPostImageRow>> imagesByPost(
			List<CommunityPostRow> rows) {
		if (rows.isEmpty()) {
			return java.util.Map.of();
		}
		return postRepository.findVisibleImagesByPostIds(rows.stream().map(CommunityPostRow::id).toList())
				.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						com.toy.nar.domain.community.repository.CommunityPostImageRow::postId));
	}

	private List<String> validateImageUrls(List<String> imageUrls) {
		if (imageUrls == null || imageUrls.isEmpty()) {
			return List.of();
		}
		if (imageUrls.size() > MAX_IMAGES) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		for (String url : imageUrls) {
			// 서명 업로드를 거친 우리 Cloudinary URL 만 — 외부 URL 주입(핫링크·우회 첨부)을 막는다
			if (!cloudinarySignatureService.isOurSecureUrl(url) || url.length() > MAX_IMAGE_URL_LENGTH) {
				throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
			}
		}
		return imageUrls;
	}

	static com.toy.nar.app.community.dto.CommunityDtos.AuthorResponse toAuthor(CommunityPostRow row) {
		if (row.authorMemberId() == null) {
			return null; // 탈퇴한 사용자 — 앱이 자리 문구를 그린다
		}
		return new com.toy.nar.app.community.dto.CommunityDtos.AuthorResponse(
				row.authorMemberId(), row.authorName() + "#" + row.authorTag(),
				row.authorProfileImageUrl(), row.authorTeamId(), row.authorTeamCode(), row.authorTeamImageUrl());
	}

	private static Long nextCursor(List<CommunityPostRow> rows, int pageSize) {
		return rows.size() < pageSize ? null : rows.get(rows.size() - 1).id();
	}

	private CommunityPost requireVisiblePost(long postId) {
		return postRepository.findById(postId)
				.filter(CommunityPost::isVisible)
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
	}

	private Member requireMember(Long memberId) {
		requireLogin(memberId);
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_LOGIN_REQUIRED));
	}

	static void requireLogin(Long memberId) {
		if (memberId == null) {
			throw new CustomException(ErrorCode.COMMUNITY_LOGIN_REQUIRED);
		}
	}

	static String requireLength(String value, int maxLength) {
		if (value == null || value.isBlank() || value.trim().length() > maxLength) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return value.trim();
	}

	int clampSize(Integer size) {
		if (size == null) {
			return DEFAULT_PAGE_SIZE;
		}
		return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
	}
}
