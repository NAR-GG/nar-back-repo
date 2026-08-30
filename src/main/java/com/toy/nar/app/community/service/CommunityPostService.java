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
	private static final int MAX_RAW_BLOCKS_LENGTH = 100_000; // 블록 JSON 원문 안전 상한

	private final CommunityPostRepository postRepository;
	private final CommunityInteractionRepository interactionRepository;
	private final MemberRepository memberRepository;
	private final CommunityWriteGuard writeGuard;
	private final CommunityBlockValidator blockValidator;
	private final org.springframework.context.ApplicationEventPublisher eventPublisher;
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
		return new PostListResponse(posts, nextCursor(rows, pageSize), boardViewer(boardTeamId, viewerId));
	}

	/**
	 * 쓰기 자격 + 작성 간격 판정. 로그인 상태면 <b>전체 게시판에도</b> 실어 준다 —
	 * 자격은 전체가 항상 통과지만 작성 간격은 전체에도 걸리기 때문이다.
	 *
	 * <p>화면과 데이터가 같이 오게 목록 응답에 실어, 앱이 쓰기 시도(403·429) 전에
	 * 잠금 바를 그리고 글쓰기 버튼을 잠근다.</p>
	 */
	private com.toy.nar.app.community.dto.CommunityDtos.BoardViewerResponse boardViewer(
			Long boardTeamId, Long viewerId) {
		if (viewerId == null) {
			return null;
		}
		return memberRepository.findById(viewerId)
				.map(member -> {
					var result = writeGuard.evaluateBoardWritability(member, boardTeamId);
					return new com.toy.nar.app.community.dto.CommunityDtos.BoardViewerResponse(
							result.canWrite(), result.reason(),
							writeGuard.nextPostWritableAt(viewerId, boardTeamId));
				})
				.orElse(null);
	}

	public PostDetailResponse getPost(long postId, Long viewerId) {
		CommunityPostRow row = postRepository.findRowById(postId)
				.filter(r -> "VISIBLE".equals(r.status()))
				.orElseThrow(() -> new CustomException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

		boolean liked = false;
		boolean scrapped = false;
		boolean mine = false;
		boolean blockedAuthor = false;
		boolean notificationEnabled = true;
		if (viewerId != null) {
			liked = interactionRepository.existsPostLike(postId, viewerId);
			scrapped = interactionRepository.existsScrap(postId, viewerId);
			mine = viewerId.equals(row.authorMemberId());
			notificationEnabled = !interactionRepository.isNotificationMuted(postId, viewerId);
			// 목록은 숨기지만 상세 직진입(딥링크·스크랩 경유)은 자리만 마스킹해서 내려준다.
			blockedAuthor = row.authorMemberId() != null
					&& interactionRepository.findBlockedMemberIds(viewerId).contains(row.authorMemberId());
		}
		PostViewerResponse viewer = new PostViewerResponse(liked, scrapped, mine, blockedAuthor,
				notificationEnabled);
		List<com.toy.nar.app.community.dto.CommunityDtos.PostImageResponse> images = blockedAuthor
				? List.of()
				: postRepository.findVisibleImages(postId).stream()
						.map(img -> new com.toy.nar.app.community.dto.CommunityDtos.PostImageResponse(
								img.id(), img.imageUrl()))
						.toList();
		return new PostDetailResponse(row.id(), row.boardTeamId(), row.boardTeamCode(),
				blockedAuthor ? null : row.title(),
				blockedAuthor ? null : row.body(),
				row.bodyFormat(),
				toAuthor(row), row.viewCount(), row.likeCount(), row.commentCount(),
				row.editedAt() != null, row.createdAt(), viewer, images);
	}

	@Transactional
	public long create(Long memberId, PostCreateRequest request) {
		Member member = requireMember(memberId);
		String title = requireLength(request.title(), MAX_TITLE_LENGTH);
		writeGuard.checkBoardWritable(member, request.boardTeamId());
		writeGuard.checkPostInterval(memberId, request.boardTeamId());

		ValidatedBody validated = validateBody(request.body(), request.bodyFormat(), request.imageUrls());

		Long authorTeamId = member.getFavoriteTeam() == null ? null : member.getFavoriteTeam().getId();
		CommunityPost post = CommunityPost.builder()
				.boardTeamId(request.boardTeamId())
				.memberId(memberId)
				.authorTeamId(authorTeamId)
				.title(title)
				.body(validated.body())
				.bodyFormat(validated.format())
				.preview(validated.preview())
				.build();
		long postId = postRepository.save(post).getId();
		if (!validated.imageUrls().isEmpty()) {
			postRepository.replaceImages(postId, validated.imageUrls());
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
		ValidatedBody validated = validateBody(request.body(), request.bodyFormat(), request.imageUrls());
		post.edit(requireLength(request.title(), MAX_TITLE_LENGTH),
				validated.body(), validated.format(), validated.preview());
		if ("BLOCKS".equals(validated.format())) {
			// 블록이 이미지의 진실 — 항상 블록에서 추출한 목록으로 동기화한다.
			postRepository.replaceImages(postId, validated.imageUrls());
		} else if (request.imageUrls() != null) {
			// PLAIN 은 기존 계약: null = 이미지 변경 없음, 빈 배열 = 전부 제거
			postRepository.replaceImages(postId, validated.imageUrls());
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
		CommunityPost post = requireVisiblePost(postId);
		ToggleResult result = interactionRepository.togglePostLike(postId, memberId);
		int likeCount = switch (result) {
			case ADDED -> postRepository.applyLikeDelta(postId, 1);
			case REMOVED -> postRepository.applyLikeDelta(postId, -1);
			case ALREADY_ADDED -> postRepository.applyLikeDelta(postId, 0);
		};
		if (result == ToggleResult.ADDED) {
			// 알림(원글 작성자)은 커밋 후 리스너가 보낸다 — 최초 1회 dedupe 도 리스너 몫.
			memberRepository.findById(memberId).ifPresent(actor ->
					eventPublisher.publishEvent(new CommunityPostLikedEvent(
							postId, memberId, actor.getNickname(), post.getMemberId(), post.getTitle())));
		}
		return new LikeToggleResponse(result != ToggleResult.REMOVED, likeCount);
	}

	/** 이 글의 알림 켬/끔 토글. mute 행 존재 = 끔 — 응답은 켜짐 여부로 뒤집어 준다. */
	@Transactional
	public com.toy.nar.app.community.dto.CommunityDtos.NotificationToggleResponse toggleNotification(
			long postId, Long memberId) {
		requireLogin(memberId);
		requireVisiblePost(postId);
		ToggleResult result = interactionRepository.toggleNotificationMute(postId, memberId);
		// ADDED = mute 생성(끔), REMOVED = mute 해제(켬). 중복 생성 레이스는 끔 유지.
		return new com.toy.nar.app.community.dto.CommunityDtos.NotificationToggleResponse(
				result == ToggleResult.REMOVED);
	}

	@Transactional
	public ScrapToggleResponse toggleScrap(long postId, Long memberId) {
		requireLogin(memberId);
		requireVisiblePost(postId);
		ToggleResult result = interactionRepository.toggleScrap(postId, memberId);
		return new ScrapToggleResponse(result != ToggleResult.REMOVED);
	}

	public PostListResponse getMyPosts(Long memberId, Long cursor, Integer size) {
		requireLogin(memberId);
		int pageSize = clampSize(size);
		List<CommunityPostRow> rows = postRepository.findMyPostPage(memberId, cursor, pageSize);
		var imagesByPost = imagesByPost(rows);
		List<PostSummaryResponse> posts = rows.stream()
				.map(row -> toSummary(row, imagesByPost.getOrDefault(row.id(), List.of())))
				.toList();
		return new PostListResponse(posts, nextCursor(rows, pageSize), null);
	}

	public com.toy.nar.app.community.dto.CommunityDtos.LikedPostListResponse getMyLikedPosts(
			Long memberId, Long cursor, Integer size) {
		requireLogin(memberId);
		int pageSize = clampSize(size);
		List<CommunityPostRow> rows = postRepository.findLikedPage(memberId, cursor, pageSize);
		var imagesByPost = imagesByPost(rows);
		var items = rows.stream()
				.map(row -> new com.toy.nar.app.community.dto.CommunityDtos.LikedPostItemResponse(
						row.scrapId(), toSummary(row, imagesByPost.getOrDefault(row.id(), List.of()))))
				.toList();
		Long nextCursor = rows.size() < pageSize ? null : rows.get(rows.size() - 1).scrapId();
		return new com.toy.nar.app.community.dto.CommunityDtos.LikedPostListResponse(items, nextCursor);
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
		// BLOCKS 는 body 가 JSON 이라 절단하면 미리보기가 깨진다 — 저장 시 계산한 preview 사용.
		String preview = "BLOCKS".equals(row.bodyFormat())
				? (row.preview() == null ? "" : row.preview())
				: (row.body().length() <= PREVIEW_LENGTH
						? row.body()
						: row.body().substring(0, PREVIEW_LENGTH));
		String thumbnailUrl = images.isEmpty() ? null : images.get(0).imageUrl();
		return new PostSummaryResponse(row.id(), row.boardTeamId(), row.boardTeamCode(), row.title(), preview,
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

	/** 검증이 끝난 본문. PLAIN 은 preview=null(목록에서 body 절단), BLOCKS 는 저장 시 계산. */
	record ValidatedBody(String body, String format, String preview, List<String> imageUrls) {
	}

	/**
	 * PLAIN 은 기존 규칙(본문 1만자 + imageUrls 검증). BLOCKS 는 블록 JSON 을 파싱·정규화하고
	 * 이미지도 블록에서 추출한다 — imageUrls 파라미터는 무시된다(블록이 진실).
	 * 블록 JSON 원문은 텍스트 1만자 + 메타라 10K 를 넘을 수 있어 원문 자체는 100K 로만 막는다.
	 */
	private ValidatedBody validateBody(String body, String bodyFormat, List<String> imageUrls) {
		if ("BLOCKS".equals(bodyFormat)) {
			if (body == null || body.isBlank() || body.length() > MAX_RAW_BLOCKS_LENGTH) {
				throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
			}
			var parsed = blockValidator.validate(body);
			return new ValidatedBody(parsed.normalizedBody(), "BLOCKS", parsed.preview(), parsed.imageUrls());
		}
		if (bodyFormat != null && !"PLAIN".equals(bodyFormat)) {
			throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return new ValidatedBody(requireLength(body, MAX_BODY_LENGTH), "PLAIN", null,
				validateImageUrls(imageUrls));
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
