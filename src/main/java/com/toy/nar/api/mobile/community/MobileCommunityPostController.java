package com.toy.nar.api.mobile.community;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.community.dto.CommunityDtos.LikeToggleResponse;
import com.toy.nar.app.community.dto.CommunityDtos.PostCreateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.PostDetailResponse;
import com.toy.nar.app.community.dto.CommunityDtos.PostListResponse;
import com.toy.nar.app.community.dto.CommunityDtos.PostUpdateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.ScrapToggleResponse;
import com.toy.nar.app.community.service.CommunityPostService;

import lombok.RequiredArgsConstructor;

/**
 * 커뮤니티 게시글 API. 목록·상세는 선택적 인증(비회원도 읽고, 로그인이면 차단 필터와
 * 내 좋아요·스크랩 여부가 붙는다), 쓰기는 SecurityConfig 에서 인증 필수로 걸려 있다.
 */
@RestController
@RequestMapping("/api/mobile/community")
@RequiredArgsConstructor
public class MobileCommunityPostController {

	private final CommunityPostService postService;

	@GetMapping("/posts")
	public ResponseEntity<PostListResponse> getPosts(
			@RequestParam(required = false) Long boardTeamId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(required = false) Integer size,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(postService.getPosts(boardTeamId, cursor, size, memberId));
	}

	@GetMapping("/posts/{postId}")
	public ResponseEntity<PostDetailResponse> getPost(
			@PathVariable long postId,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(postService.getPost(postId, memberId));
	}

	@PostMapping("/posts")
	public ResponseEntity<Map<String, Long>> create(
			@RequestBody PostCreateRequest request,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(Map.of("id", postService.create(memberId, request)));
	}

	@PutMapping("/posts/{postId}")
	public ResponseEntity<Void> update(
			@PathVariable long postId,
			@RequestBody PostUpdateRequest request,
			@AuthenticationPrincipal Long memberId) {
		postService.update(postId, memberId, request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/posts/{postId}")
	public ResponseEntity<Void> delete(
			@PathVariable long postId,
			@AuthenticationPrincipal Long memberId) {
		postService.delete(postId, memberId);
		return ResponseEntity.noContent().build();
	}

	/** 조회수 +1. 비회원 포함, 실패해도 무시되는 참고용 숫자(D-4 중복 허용). */
	@PostMapping("/posts/{postId}/view")
	public ResponseEntity<Void> increaseViewCount(@PathVariable long postId) {
		postService.increaseViewCount(postId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/posts/{postId}/like")
	public ResponseEntity<LikeToggleResponse> toggleLike(
			@PathVariable long postId,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(postService.toggleLike(postId, memberId));
	}

	/** 이 글의 알림 켬/끔 토글 — 끄면 이 글에서 오는 댓글·답글 알림이 안 온다. */
	@PostMapping("/posts/{postId}/notification")
	public ResponseEntity<com.toy.nar.app.community.dto.CommunityDtos.NotificationToggleResponse> toggleNotification(
			@PathVariable long postId,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(postService.toggleNotification(postId, memberId));
	}

	@PostMapping("/posts/{postId}/scrap")
	public ResponseEntity<ScrapToggleResponse> toggleScrap(
			@PathVariable long postId,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(postService.toggleScrap(postId, memberId));
	}
}
