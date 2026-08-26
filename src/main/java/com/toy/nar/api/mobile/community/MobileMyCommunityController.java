package com.toy.nar.api.mobile.community;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toy.nar.app.community.dto.CommunityDtos.ScrapListResponse;
import com.toy.nar.app.community.service.CommunityPostService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/mobile/me/community")
@RequiredArgsConstructor
public class MobileMyCommunityController {

	private final CommunityPostService postService;

	@GetMapping("/scraps")
	public ResponseEntity<ScrapListResponse> getMyScraps(
			@RequestParam(required = false) Long cursor,
			@RequestParam(required = false) Integer size,
			@AuthenticationPrincipal Long memberId) {
		return ResponseEntity.ok(postService.getMyScraps(memberId, cursor, size));
	}
}
