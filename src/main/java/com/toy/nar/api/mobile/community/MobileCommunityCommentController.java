package com.toy.nar.api.mobile.community;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.community.dto.CommunityDtos.CommentCreateRequest;
import com.toy.nar.app.community.dto.CommunityDtos.CommentListResponse;
import com.toy.nar.app.community.dto.CommunityDtos.LikeToggleResponse;
import com.toy.nar.app.community.service.CommunityCommentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/mobile/community")
@RequiredArgsConstructor
public class MobileCommunityCommentController {

	private final CommunityCommentService commentService;

	@GetMapping("/posts/{postId}/comments")
	public ResponseEntity<CommentListResponse> getComments(
			@PathVariable long postId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(required = false) Integer size,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(commentService.getComments(postId, cursor, size, memberId));
	}

	@PostMapping("/posts/{postId}/comments")
	public ResponseEntity<Map<String, Long>> create(
			@PathVariable long postId,
			@RequestBody CommentCreateRequest request,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(Map.of("id", commentService.create(postId, memberId, request)));
	}

	@org.springframework.web.bind.annotation.PutMapping("/comments/{commentId}")
	public ResponseEntity<Void> update(
			@PathVariable long commentId,
			@RequestBody com.toy.nar.app.community.dto.CommunityDtos.CommentUpdateRequest request,
			@AuthenticationPrincipal Long memberId) {
		commentService.update(commentId, memberId, request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/comments/{commentId}")
	public ResponseEntity<Void> delete(
			@PathVariable long commentId,
			@AuthenticationPrincipal Long memberId) {
		commentService.delete(commentId, memberId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/comments/{commentId}/like")
	public ResponseEntity<LikeToggleResponse> toggleLike(
			@PathVariable long commentId,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(commentService.toggleLike(commentId, memberId));
	}
}
